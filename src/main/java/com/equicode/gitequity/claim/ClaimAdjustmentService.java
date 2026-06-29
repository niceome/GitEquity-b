package com.equicode.gitequity.claim;

import com.equicode.gitequity.domain.ClaimStatus;
import com.equicode.gitequity.domain.NonCodeContribution;
import com.equicode.gitequity.equity.EquityCalculatorService;
import com.equicode.gitequity.equity.EquityResult;
import com.equicode.gitequity.equity.UserEquity;
import com.equicode.gitequity.repository.NonCodeContributionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 확정된 비코드 기여(claim)를 반영해 {@link EquityCalculatorService}의 기본 지분을 보정한다.
 *
 * 1. status=CONFIRMED인 claim을 수혜자별로 유형 가중치({@link com.equicode.gitequity.domain.NonCodeContributionType})
 *    합산 → weightedScore
 * 2. weightedScore → multiplier ({@link ClaimAdjustmentCalculator}, ±10% 클램프)
 * 3. adjustedRaw = basePercentage × multiplier
 * 4. adjustedRaw 합이 100%가 되도록 재정규화
 */
@Service
@RequiredArgsConstructor
public class ClaimAdjustmentService {

    private final EquityCalculatorService equityCalculatorService;
    private final NonCodeContributionRepository nonCodeContributionRepository;
    private final ClaimAdjustmentCalculator calculator;

    @Transactional(readOnly = true)
    public EquityResult applyAdjustment(Long projectId) {
        EquityResult base = equityCalculatorService.calculate(projectId);
        if (base.equities().isEmpty()) {
            return base;
        }

        Map<Long, Double> weightedScoreByBeneficiary = new HashMap<>();
        for (NonCodeContribution claim : nonCodeContributionRepository.findByProjectIdAndStatus(projectId, ClaimStatus.CONFIRMED)) {
            weightedScoreByBeneficiary.merge(claim.getBeneficiary().getId(), claim.getType().getWeight(), Double::sum);
        }

        List<UserEquity> adjusted = base.equities().stream()
                .map(e -> {
                    double score = weightedScoreByBeneficiary.getOrDefault(e.userId(), 0.0);
                    double multiplier = calculator.multiplier(score);
                    return new UserEquity(e.userId(), e.username(), e.rawScore(), e.percentage() * multiplier, e.byType());
                })
                .toList();

        double sum = adjusted.stream().mapToDouble(UserEquity::percentage).sum();

        List<UserEquity> renormalized = adjusted.stream()
                .map(e -> new UserEquity(
                        e.userId(), e.username(), e.rawScore(),
                        sum == 0.0 ? 0.0 : Math.round(e.percentage() / sum * 10_000.0) / 100.0,
                        e.byType()))
                .sorted(Comparator.comparingDouble(UserEquity::percentage).reversed())
                .toList();

        return new EquityResult(base.projectId(), renormalized, base.totalRawScore(), base.calculatedAt(), base.algorithmVersion());
    }
}
