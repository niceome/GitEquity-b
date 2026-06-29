package com.equicode.gitequity.equity;

import com.equicode.gitequity.domain.PrContribution;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.equity.dmm.DmmFactor;
import com.equicode.gitequity.github.collector.CommunicationScoreService;
import com.equicode.gitequity.repository.PrContributionRepository;
import com.equicode.gitequity.repository.ProjectRepository;
import com.equicode.gitequity.repository.ReviewContributionRepository;
import com.equicode.gitequity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 가중치 최적화 검증 (역방향 접근)
 *
 * 기존 SyntheticGroundTruthTest: raw → 공식 → 기대값 (항등 검증)
 * 이 테스트: 의도된 지분%(정답) → 노이즈 포함 raw 생성 → 가중치별 정답 복원력 비교
 *
 * 노이즈가 각 차원(PR/리뷰/코멘트)에 독립적으로 적용되므로
 * 가중치 선택에 따라 결과가 달라지며, 최적 가중치를 탐색할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class WeightOptimizationTest {

    private static final int PROJECT_COUNT = 100;
    private static final long SEED = 42L;

    private static final double BASE_PR_LINES = 500.0;
    private static final double BASE_REVIEW_SCORE = 20.0;
    private static final double BASE_COMMENT_SCORE = 50.0;

    @Mock PrContributionRepository prContributionRepository;
    @Mock ReviewContributionRepository reviewContributionRepository;
    @Mock CommunicationScoreService communicationScoreService;
    @Mock UserRepository userRepository;
    @Mock ProjectRepository projectRepository;
    @Spy  DmmFactor dmmFactor = new DmmFactor(0.7, 0.6);
    @Spy  ExplorationCapCalculator explorationCapCalculator = new ExplorationCapCalculator(0.15);
    @Spy  EquityWeights equityWeights = new EquityWeights(0.25, 0.50, 0.25);

    @InjectMocks
    EquityCalculatorService service;

    // ── 데이터 구조 ──────────────────────────────────────────────────────

    record Member(long githubId, User user, double intendedPct,
                  int prNetLines, double reviewScore, double commentScore) {}

    record Scenario(long projectId, List<Member> members) {}

    record WeightCombo(int pr, int rev, int com) {
        double[] normalized() {
            double sum = pr + rev + com;
            return new double[]{pr / sum, rev / sum, com / sum};
        }
        String label() { return "(%d:%d:%d)".formatted(pr, rev, com); }
    }

    record EvalResult(WeightCombo weights, double rmse, double spearman, double exactRecoveryRate) {}

    // ── 메인 테스트: 125 가중치 grid search ──────────────────────────────

    @Test
    @DisplayName("125가지 가중치 × 100 프로젝트 — 노이즈(±30%) 하에서 최적 가중치 탐색")
    void weightGridSearch() {
        List<EvalResult> results = runGridSearch(0.30);

        EvalResult current = results.stream()
                .filter(r -> r.weights().pr() == 1 && r.weights().rev() == 2 && r.weights().com() == 1)
                .findFirst().orElseThrow();
        int currentRank = results.indexOf(current) + 1;

        assertThat(currentRank)
                .as("현재 가중치 (1:2:1)이 상위 50%% 이내")
                .isLessThanOrEqualTo(results.size() / 2);
    }

    @Test
    @DisplayName("노이즈 강도별 강건성 비교 — ±10%, ±30%, ±50%")
    void robustnessAcrossNoiseLevels() {
        for (double noise : new double[]{0.10, 0.30, 0.50}) {
            System.out.printf("%n══════ 노이즈 ±%.0f%% ══════%n", noise * 100);
            List<EvalResult> results = runGridSearch(noise);

            EvalResult best = results.get(0);
            assertThat(best.spearman())
                    .as("노이즈 ±%.0f%%에서 최적 가중치의 Spearman ≥ 0.8", noise * 100)
                    .isGreaterThanOrEqualTo(0.8);
        }
    }

    // ── grid search 실행 ─────────────────────────────────────────────────

    private List<EvalResult> runGridSearch(double noiseRange) {
        Random rng = new Random(SEED);
        List<Scenario> scenarios = new ArrayList<>();
        for (int i = 0; i < PROJECT_COUNT; i++) {
            scenarios.add(generateScenario(rng, i, noiseRange));
        }

        // [최적화 1] 이전 루프에서 누적된 Mockito 스터빙 기록을 초기화하여 메모리 병목 방지
        reset(projectRepository, prContributionRepository, reviewContributionRepository, communicationScoreService, userRepository);

        // [최적화 2] 시나리오 스터빙은 가중치 변동과 무관하므로 바깥 루프에서 딱 한 번만 수행
        for (Scenario s : scenarios) {
            stub(s);
        }

        List<WeightCombo> combos = new ArrayList<>();
        for (int pr = 1; pr <= 5; pr++)
            for (int rev = 1; rev <= 5; rev++)
                for (int com = 1; com <= 5; com++)
                    combos.add(new WeightCombo(pr, rev, com));

        List<EvalResult> results = new ArrayList<>();

        for (WeightCombo combo : combos) {
            doReturn(combo.normalized()).when(equityWeights).defaults();

            double totalRmse = 0;
            double totalSpearman = 0;
            int exactCount = 0;

            for (Scenario s : scenarios) {
                // 기존 위치에서 stub(s) 제거하여 12,500번의 중복 연산 및 누적을 차단

                EquityResult result = service.calculate(s.projectId());

                Map<String, Double> actualPcts = new LinkedHashMap<>();
                for (UserEquity ue : result.equities()) {
                    actualPcts.put(ue.username(), ue.percentage());
                }

                double[] intended = s.members().stream()
                        .mapToDouble(Member::intendedPct).toArray();
                double[] actual = s.members().stream()
                        .mapToDouble(m -> actualPcts.getOrDefault(m.user().getUsername(), 0.0))
                        .toArray();

                totalRmse += rmse(intended, actual);
                totalSpearman += spearman(intended, actual);

                List<String> intendedRanking = s.members().stream()
                        .sorted(Comparator.comparingDouble(Member::intendedPct).reversed())
                        .map(m -> m.user().getUsername()).toList();
                List<String> actualRanking = result.equities().stream()
                        .map(UserEquity::username).toList();
                if (intendedRanking.equals(actualRanking)) exactCount++;
            }

            results.add(new EvalResult(combo,
                    totalRmse / PROJECT_COUNT,
                    totalSpearman / PROJECT_COUNT,
                    (double) exactCount / PROJECT_COUNT * 100));
        }

        results.sort(Comparator.comparingDouble(EvalResult::rmse));

        System.out.printf("%n%-5s %-18s %-8s %-10s %s%n",
                "순위", "가중치(PR:Rev:Com)", "RMSE", "Spearman", "완전복원율");
        System.out.println("─".repeat(60));
        for (int i = 0; i < results.size(); i++) {
            EvalResult r = results.get(i);
            System.out.printf("%-5d %-18s %-8.2f %-10.3f %.0f%%%n",
                    i + 1, r.weights().label(), r.rmse(), r.spearman(), r.exactRecoveryRate());
        }

        EvalResult current = results.stream()
                .filter(r -> r.weights().pr() == 1 && r.weights().rev() == 2 && r.weights().com() == 1)
                .findFirst().orElseThrow();
        int currentRank = results.indexOf(current) + 1;
        System.out.printf("%n현재 설정 (1:2:1) 순위: %d / %d%n", currentRank, results.size());

        return results;
    }

    // ── 시나리오 생성 ────────────────────────────────────────────────────

    private Scenario generateScenario(Random rng, int index, double noiseRange) {
        long projectId = index + 1;
        int memberCount = 3 + rng.nextInt(3);

        double[] rawShares = new double[memberCount];
        for (int m = 0; m < memberCount; m++) {
            rawShares[m] = 1.0 + rng.nextDouble() * 9.0;
        }
        double shareSum = 0;
        for (double s : rawShares) shareSum += s;

        double[] intendedPcts = new double[memberCount];
        for (int m = 0; m < memberCount; m++) {
            intendedPcts[m] = rawShares[m] / shareSum * 100.0;
        }

        List<Member> members = new ArrayList<>();
        for (int m = 0; m < memberCount; m++) {
            long gid = projectId * 1000 + m + 1;
            double pct = intendedPcts[m] / 100.0;

            double prNoise = 1.0 + (rng.nextDouble() * 2 - 1) * noiseRange;
            double revNoise = 1.0 + (rng.nextDouble() * 2 - 1) * noiseRange;
            double comNoise = 1.0 + (rng.nextDouble() * 2 - 1) * noiseRange;

            int prLines = Math.max(1, (int) (pct * BASE_PR_LINES * prNoise));
            double revScore = Math.max(0.1, pct * BASE_REVIEW_SCORE * revNoise);
            double comScore = Math.max(0.1, pct * BASE_COMMENT_SCORE * comNoise);

            members.add(new Member(gid,
                    User.builder().id(gid).githubId(gid)
                            .username("p%d-u%d".formatted(index, m)).build(),
                    intendedPcts[m], prLines, revScore, comScore));
        }

        return new Scenario(projectId, members);
    }

    // ── Mock 설정 ────────────────────────────────────────────────────────

    private void stub(Scenario s) {
        lenient().when(projectRepository.existsById(s.projectId())).thenReturn(true);

        List<PrContribution> prs = new ArrayList<>();
        int prNum = 1;
        for (Member m : s.members()) {
            prs.add(PrContribution.builder()
                    .authorGithubId(m.githubId()).prNumber(prNum++).title("synth")
                    .isMerged(true).additions(m.prNetLines()).deletions(0).netLines(m.prNetLines())
                    .density(1.0).prType("FEAT").dmmComplexity(null).coAuthorGithubIds(null)
                    .build());
        }
        lenient().when(prContributionRepository.findByProjectId(s.projectId())).thenReturn(prs);

        List<Object[]> revRows = s.members().stream()
                .map(m -> new Object[]{m.githubId(), m.reviewScore()}).toList();
        lenient().when(reviewContributionRepository.sumScoreGroupByReviewer(s.projectId()))
                .thenReturn(revRows);
        lenient().when(reviewContributionRepository.findDistinctPrNumbersByProjectId(s.projectId()))
                .thenReturn(List.of());

        Map<Long, Double> comMap = new LinkedHashMap<>();
        s.members().forEach(m -> comMap.put(m.githubId(), m.commentScore()));
        lenient().when(communicationScoreService.calculate(s.projectId())).thenReturn(comMap);

        for (Member m : s.members()) {
            lenient().when(userRepository.findByGithubId(m.githubId()))
                    .thenReturn(Optional.of(m.user()));
        }
    }

    // ── 평가 지표 ────────────────────────────────────────────────────────

    private double rmse(double[] expected, double[] actual) {
        double sum = 0;
        for (int i = 0; i < expected.length; i++) {
            double diff = expected[i] - actual[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum / expected.length);
    }

    private double spearman(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) return 1.0;

        double[] rankX = ranks(x);
        double[] rankY = ranks(y);

        double sumD2 = 0;
        for (int i = 0; i < n; i++) {
            double d = rankX[i] - rankY[i];
            sumD2 += d * d;
        }
        return 1.0 - (6.0 * sumD2) / (n * ((double) n * n - 1.0));
    }

    private double[] ranks(double[] values) {
        int n = values.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(values[b], values[a]));

        double[] result = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j < n - 1 && Math.abs(values[indices[j]] - values[indices[j + 1]]) < 1e-9) {
                j++;
            }
            double avgRank = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) {
                result[indices[k]] = avgRank;
            }
            i = j + 1;
        }
        return result;
    }
}