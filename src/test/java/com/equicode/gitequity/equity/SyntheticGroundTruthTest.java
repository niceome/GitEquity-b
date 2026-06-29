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
 * 합성 데이터(Synthetic Ground Truth) 검증
 *
 * 기여도를 미리 설계한 가상 팀 100개를 생성하고,
 * 알고리즘이 의도한 순위와 지분율을 정확히 복원하는지 전수 검증한다.
 *
 * 각 프로젝트: 3~5명, 개인별 PR/리뷰/코멘트 기여도 랜덤 생성 (seed=42, 재현 가능)
 */
@ExtendWith(MockitoExtension.class)
class SyntheticGroundTruthTest {

    private static final int PROJECT_COUNT = 100;
    private static final long SEED = 42L;
    private static final double W_PR = 0.25;
    private static final double W_REVIEW = 0.50;
    private static final double W_COMMENT = 0.25;

    @Mock PrContributionRepository prContributionRepository;
    @Mock ReviewContributionRepository reviewContributionRepository;
    @Mock CommunicationScoreService communicationScoreService;
    @Mock UserRepository userRepository;
    @Mock ProjectRepository projectRepository;
    @Spy  DmmFactor dmmFactor = new DmmFactor(0.7, 0.6);
    @Spy  ExplorationCapCalculator explorationCapCalculator = new ExplorationCapCalculator(0.15);
    @Spy  EquityWeights equityWeights = new EquityWeights(W_PR, W_REVIEW, W_COMMENT);

    @InjectMocks
    EquityCalculatorService service;

    // ── 합성 데이터 구조 ──────────────────────────────────────────────────

    record Member(long githubId, User user, int prNetLines, double reviewScore, double commentScore) {}

    record Scenario(long projectId, int memberCount, List<Member> members,
                    List<String> expectedRanking, Map<String, Double> expectedPct) {}

    // ── 1. 순위 복원 검증 ────────────────────────────────────────────────

    @Test
    @DisplayName("100개 합성 프로젝트 — 알고리즘이 의도한 기여 순위를 복원한다")
    void rankRecovery() {
        Random rng = new Random(SEED);
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < PROJECT_COUNT; i++) {
            Scenario s = generate(rng, i);
            stub(s);
            EquityResult result = service.calculate(s.projectId());

            List<String> actual = result.equities().stream()
                    .map(UserEquity::username).toList();

            if (!rankingMatchesWithTies(s.expectedRanking(), s.expectedPct(), actual)) {
                failures.add("project-%d(%d명): expected=%s actual=%s".formatted(
                        i, s.memberCount(), s.expectedRanking(), actual));
            }
        }

        assertThat(failures).as("순위 복원 실패 목록").isEmpty();
    }

    // ── 2. 지분율 정확도 검증 ────────────────────────────────────────────

    @Test
    @DisplayName("100개 합성 프로젝트 — 산출 지분율이 기대값과 ±0.01%p 이내 일치")
    void percentageAccuracy() {
        Random rng = new Random(SEED);
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < PROJECT_COUNT; i++) {
            Scenario s = generate(rng, i);
            stub(s);
            EquityResult result = service.calculate(s.projectId());

            for (UserEquity ue : result.equities()) {
                Double expected = s.expectedPct().get(ue.username());
                if (expected == null || Math.abs(ue.percentage() - expected) > 0.01) {
                    failures.add("project-%d %s: expected=%.2f%% actual=%.2f%%".formatted(
                            i, ue.username(), expected, ue.percentage()));
                }
            }

            assertThat(result.isSumValid()).as("project-%d 합계=100%%", i).isTrue();
        }

        assertThat(failures).as("지분율 오차 목록").isEmpty();
    }

    // ── 3. 지분 합계 100% 검증 ───────────────────────────────────────────

    @Test
    @DisplayName("100개 합성 프로젝트 — 모든 프로젝트의 지분 합계가 100%")
    void sumAlways100() {
        Random rng = new Random(SEED);

        for (int i = 0; i < PROJECT_COUNT; i++) {
            Scenario s = generate(rng, i);
            stub(s);
            EquityResult result = service.calculate(s.projectId());

            double sum = result.equities().stream().mapToDouble(UserEquity::percentage).sum();
            assertThat(sum).as("project-%d", i).isCloseTo(100.0, within(0.1));
        }
    }

    // ── 4. 직관 시나리오: A≈70%, B≈30%, C≈1% ───────────────────────────

    @Test
    @DisplayName("직관 시나리오 — 핵심 기여자(≈69%) > 보조(≈30%) > 프리라이더(≈1%)")
    void handcrafted_dominantContributor() {
        long projectId = 9999L;
        List<Member> members = List.of(
                new Member(201L, user(1L, 201L, "core-dev"),  350, 14.0, 35.0),
                new Member(202L, user(2L, 202L, "sub-dev"),   150,  6.0, 15.0),
                new Member(203L, user(3L, 203L, "freerider"),   5,  0.2,  0.5));

        Scenario s = computeExpected(projectId, members);
        stub(s);
        EquityResult result = service.calculate(projectId);

        List<String> ranking = result.equities().stream()
                .map(UserEquity::username).toList();
        assertThat(ranking).containsExactly("core-dev", "sub-dev", "freerider");

        assertThat(result.forUser(1L).percentage()).isCloseTo(69.31, within(0.01));
        assertThat(result.forUser(2L).percentage()).isCloseTo(29.70, within(0.01));
        assertThat(result.forUser(3L).percentage()).isCloseTo(0.99,  within(0.01));
    }

    // ── 5. 차원별 전문가 시나리오 ────────────────────────────────────────

    @Test
    @DisplayName("리뷰 전문가가 PR 전문가보다 높은 지분을 받는다 (리뷰 가중치=0.50)")
    void reviewSpecialist_outranks_prSpecialist() {
        long projectId = 9998L;
        List<Member> members = List.of(
                new Member(301L, user(11L, 301L, "pr-heavy"),     400, 2.0,  5.0),
                new Member(302L, user(12L, 302L, "review-heavy"),  50, 18.0, 5.0));

        Scenario s = computeExpected(projectId, members);
        stub(s);
        EquityResult result = service.calculate(projectId);

        assertThat(result.equities().get(0).username()).isEqualTo("review-heavy");
    }

    // ── 시나리오 생성기 ───────────────────────────────────────────────────

    private Scenario generate(Random rng, int index) {
        long projectId = index + 1;
        int count = 3 + rng.nextInt(3);

        List<Member> members = new ArrayList<>();
        for (int m = 0; m < count; m++) {
            long gid = projectId * 1000 + m + 1;
            members.add(new Member(gid,
                    user(gid, gid, "p%d-u%d".formatted(index, m)),
                    1 + rng.nextInt(500),
                    0.1 + rng.nextDouble() * 19.9,
                    0.1 + rng.nextDouble() * 49.9));
        }
        return computeExpected(projectId, members);
    }

    private static User user(long id, long githubId, String name) {
        return User.builder().id(id).githubId(githubId).username(name).build();
    }

    // ── 레퍼런스 구현 (기대값 계산) ───────────────────────────────────────

    private Scenario computeExpected(long projectId, List<Member> members) {
        double maxPr  = members.stream().mapToDouble(Member::prNetLines).max().orElse(0);
        double maxRev = members.stream().mapToDouble(Member::reviewScore).max().orElse(0);
        double maxCom = members.stream().mapToDouble(Member::commentScore).max().orElse(0);

        Map<String, Double> totals = new LinkedHashMap<>();
        for (Member m : members) {
            double nPr  = maxPr  == 0 ? 0 : m.prNetLines() / maxPr;
            double nRev = maxRev == 0 ? 0 : m.reviewScore() / maxRev;
            double nCom = maxCom == 0 ? 0 : m.commentScore() / maxCom;
            totals.put(m.user().getUsername(), W_PR * nPr + W_REVIEW * nRev + W_COMMENT * nCom);
        }

        double grand = totals.values().stream().mapToDouble(Double::doubleValue).sum();

        Map<String, Double> pcts = new LinkedHashMap<>();
        totals.forEach((name, total) ->
                pcts.put(name, grand == 0 ? 0 : Math.round(total / grand * 10_000.0) / 100.0));

        List<String> ranking = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();

        return new Scenario(projectId, members.size(), members, ranking, pcts);
    }

    // ── Mock 설정 ─────────────────────────────────────────────────────────

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
        lenient().when(reviewContributionRepository.sumScoreGroupByReviewer(s.projectId())).thenReturn(revRows);
        lenient().when(reviewContributionRepository.findDistinctPrNumbersByProjectId(s.projectId()))
                .thenReturn(List.of());

        Map<Long, Double> comMap = new LinkedHashMap<>();
        s.members().forEach(m -> comMap.put(m.githubId(), m.commentScore()));
        lenient().when(communicationScoreService.calculate(s.projectId())).thenReturn(comMap);

        for (Member m : s.members()) {
            lenient().when(userRepository.findByGithubId(m.githubId())).thenReturn(Optional.of(m.user()));
        }
    }

    // ── 순위 비교 (동률 허용) ─────────────────────────────────────────────

    private boolean rankingMatchesWithTies(List<String> expected, Map<String, Double> pcts,
                                           List<String> actual) {
        if (expected.size() != actual.size()) return false;

        int idx = 0;
        for (List<String> group : groupByPct(expected, pcts)) {
            Set<String> slice = new HashSet<>();
            for (int i = 0; i < group.size() && idx < actual.size(); i++, idx++) {
                slice.add(actual.get(idx));
            }
            if (!slice.equals(new HashSet<>(group))) return false;
        }
        return true;
    }

    private List<List<String>> groupByPct(List<String> ranking, Map<String, Double> pcts) {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        Double prev = null;

        for (String name : ranking) {
            double pct = pcts.get(name);
            if (prev != null && Math.abs(pct - prev) > 0.001) {
                groups.add(current);
                current = new ArrayList<>();
            }
            current.add(name);
            prev = pct;
        }
        if (!current.isEmpty()) groups.add(current);
        return groups;
    }
}
