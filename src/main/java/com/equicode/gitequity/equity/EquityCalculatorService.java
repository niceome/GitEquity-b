package com.equicode.gitequity.equity;

import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.common.exception.ErrorCode;
import com.equicode.gitequity.domain.ContributionType;
import com.equicode.gitequity.domain.PrContribution;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.equity.dmm.DmmFactor;
import com.equicode.gitequity.equity.quality.PrType;
import com.equicode.gitequity.github.collector.CommunicationScoreService;
import com.equicode.gitequity.repository.PrContributionRepository;
import com.equicode.gitequity.repository.ProjectRepository;
import com.equicode.gitequity.repository.ReviewContributionRepository;
import com.equicode.gitequity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 통합 지분 계산 서비스 (개선된 공식, ALGORITHM_VERSION="2.0.0")
 *
 * PR 점수 (PR 1건) =
 *   net_lines × density × prType.weight × dmmFactor(dmm) × (merged ? 1.0 : 0.3)
 *
 *   - closed-unmerged PR(S11 "탐색 기여")은 0.3 가중치를 적용하되,
 *     리뷰가 1건 이상 달린 PR만 인정하고, 사용자별 탐색 점수 총합은
 *     본인 merged PR 점수 합의 일정 비율(기본 15%, {@link ExplorationCapCalculator})로 제한한다.
 *   - Co-authored-by 공동 작성자가 있으면 PR 점수를 작성자+공동작성자 균등 분할한다 (S12).
 *
 * 사용자 총점 =
 *   w1×norm(PR점수합) + w2×norm(리뷰점수합) + w3×norm(코멘트점수합)
 *
 *   - norm()은 팀 내 최대값 기준 정규화, w는 {@link EquityWeights} 기본값 [0.50, 0.25, 0.25]
 *
 * 지분 = 사용자 총점 / 팀 전체 총점 합
 *
 * 개선 전 공식은 {@link LegacyEquityCalculatorService}(1.0.0)로 보존한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquityCalculatorService {

    public static final String ALGORITHM_VERSION = AlgorithmVersion.CURRENT;

    private static final double MERGED_FACTOR = 1.0;
    private static final double UNMERGED_FACTOR = 0.3; // S11 — closed-unmerged PR(탐색 기여) 가중치

    private final PrContributionRepository prContributionRepository;
    private final ReviewContributionRepository reviewContributionRepository;
    private final CommunicationScoreService communicationScoreService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final DmmFactor dmmFactor;
    private final ExplorationCapCalculator explorationCapCalculator;
    private final EquityWeights equityWeights;

    @Transactional(readOnly = true)
    public EquityResult calculate(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new CustomException(ErrorCode.PROJECT_NOT_FOUND);
        }

        Map<Long, Double> prScoreSum = calculatePrScoreSums(projectId);
        Map<Long, Double> reviewScoreSum = sumByKey(reviewContributionRepository.sumScoreGroupByReviewer(projectId));
        Map<Long, Double> commentScoreSum = communicationScoreService.calculate(projectId);

        Set<Long> githubIds = new LinkedHashSet<>();
        githubIds.addAll(prScoreSum.keySet());
        githubIds.addAll(reviewScoreSum.keySet());
        githubIds.addAll(commentScoreSum.keySet());

        if (githubIds.isEmpty()) {
            log.info("[Equity] project={} has no contributions", projectId);
            return new EquityResult(projectId, List.of(), 0.0, LocalDateTime.now(), ALGORITHM_VERSION);
        }

        double maxPr = max(prScoreSum);
        double maxReview = max(reviewScoreSum);
        double maxComment = max(commentScoreSum);

        double[] weights = equityWeights.defaults();

        Map<Long, Double> userTotals = new LinkedHashMap<>();
        Map<Long, User> usersByGithubId = new LinkedHashMap<>();

        for (Long githubId : githubIds) {
            User user = userRepository.findByGithubId(githubId).orElse(null);
            if (user == null) {
                log.warn("[Equity] project={} githubId={} not registered as User — skipped", projectId, githubId);
                continue;
            }
            usersByGithubId.put(githubId, user);

            double normPr = norm(prScoreSum.getOrDefault(githubId, 0.0), maxPr);
            double normReview = norm(reviewScoreSum.getOrDefault(githubId, 0.0), maxReview);
            double normComment = norm(commentScoreSum.getOrDefault(githubId, 0.0), maxComment);

            userTotals.put(githubId, weights[0] * normPr + weights[1] * normReview + weights[2] * normComment);
        }

        double grandTotal = userTotals.values().stream().mapToDouble(Double::doubleValue).sum();

        List<UserEquity> equities = userTotals.entrySet().stream()
                .map(entry -> {
                    Long githubId = entry.getKey();
                    User user = usersByGithubId.get(githubId);
                    double total = entry.getValue();
                    double percentage = grandTotal == 0.0 ? 0.0 : Math.round(total / grandTotal * 10_000.0) / 100.0;

                    Map<ContributionType, Double> byType = new EnumMap<>(ContributionType.class);
                    byType.put(ContributionType.PR, prScoreSum.getOrDefault(githubId, 0.0));
                    byType.put(ContributionType.REVIEW, reviewScoreSum.getOrDefault(githubId, 0.0));
                    byType.put(ContributionType.COMMENT, commentScoreSum.getOrDefault(githubId, 0.0));

                    return new UserEquity(user.getId(), user.getUsername(), total, percentage, byType);
                })
                .sorted(Comparator.comparingDouble(UserEquity::percentage).reversed())
                .toList();

        EquityResult result = new EquityResult(projectId, equities, grandTotal, LocalDateTime.now(), ALGORITHM_VERSION);

        log.info("[Equity] project={} contributors={} grandTotal={}",
                projectId, equities.size(), String.format("%.4f", grandTotal));
        equities.forEach(e ->
                log.info("  {} → score={} equity={}%",
                        e.username(),
                        String.format("%.4f", e.rawScore()),
                        String.format("%.2f", e.percentage())));

        return result;
    }

    // ── PR 점수 합 (merged + cap 적용된 탐색 기여, co-author 균등 분할 포함) ────────

    private Map<Long, Double> calculatePrScoreSums(Long projectId) {
        List<PrContribution> prs = prContributionRepository.findByProjectId(projectId);
        Set<Integer> reviewedPrNumbers =
                new HashSet<>(reviewContributionRepository.findDistinctPrNumbersByProjectId(projectId));

        Map<Long, Double> mergedSum = new LinkedHashMap<>();
        Map<Long, Double> explorationRawSum = new LinkedHashMap<>();

        for (PrContribution pr : prs) {
            double score = prScore(pr);
            if (score == 0.0) continue;

            List<Long> recipients = recipients(pr);
            double perRecipient = score / recipients.size();

            if (pr.isMerged()) {
                for (Long githubId : recipients) {
                    mergedSum.merge(githubId, perRecipient, Double::sum);
                }
            } else if (reviewedPrNumbers.contains(pr.getPrNumber())) {
                // closed-unmerged PR은 리뷰가 1건 이상 달린 경우만 "탐색 기여"로 인정 (S11)
                for (Long githubId : recipients) {
                    explorationRawSum.merge(githubId, perRecipient, Double::sum);
                }
            }
        }

        Map<Long, Double> result = new LinkedHashMap<>(mergedSum);
        explorationRawSum.forEach((githubId, raw) -> {
            double merged = mergedSum.getOrDefault(githubId, 0.0);
            double capped = explorationCapCalculator.apply(raw, merged);
            result.merge(githubId, capped, Double::sum);
        });

        return result;
    }

    private double prScore(PrContribution pr) {
        PrType type = parsePrType(pr.getPrType());
        double density = pr.getDensity() != null ? pr.getDensity() : 0.0;
        double mergedFactor = pr.isMerged() ? MERGED_FACTOR : UNMERGED_FACTOR;
        return pr.getNetLines() * density * type.getWeight() * dmmFactor.factor(pr.getDmmComplexity()) * mergedFactor;
    }

    private PrType parsePrType(String prType) {
        if (prType == null) return PrType.UNKNOWN;
        try {
            return PrType.valueOf(prType);
        } catch (IllegalArgumentException e) {
            return PrType.UNKNOWN;
        }
    }

    // 작성자 + 공동작성자(중복 제거) — Co-authored-by 균등 분할 (S12)
    private List<Long> recipients(PrContribution pr) {
        List<Long> coAuthors = pr.getCoAuthorGithubIds();
        if (coAuthors == null || coAuthors.isEmpty()) {
            return List.of(pr.getAuthorGithubId());
        }
        Set<Long> unique = new LinkedHashSet<>();
        unique.add(pr.getAuthorGithubId());
        unique.addAll(coAuthors);
        return new ArrayList<>(unique);
    }

    // ── 공통 헬퍼 ─────────────────────────────────────────────────────────────

    private Map<Long, Double> sumByKey(List<Object[]> rows) {
        Map<Long, Double> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], ((Number) row[1]).doubleValue());
        }
        return map;
    }

    private double max(Map<Long, Double> values) {
        return values.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    private double norm(double value, double max) {
        return max == 0.0 ? 0.0 : value / max;
    }
}
