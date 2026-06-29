package com.equicode.gitequity.equity.comparison;

import java.time.LocalDateTime;
import java.util.List;

/**
 * S10 — 개선 전(legacy, 1.0.0) vs 개선 후(신규, 2.0.0) 지분 비교 리포트
 *
 * pearsonLegacyVsPeer / pearsonNewVsPeer: 동료 평가 점수가 주어진 경우에만 계산되며,
 * 그렇지 않거나 표본이 부족하면 null이다.
 */
public record ComparisonReport(
        Long projectId,
        List<UserComparison> users,
        Double pearsonLegacyVsPeer,
        Double pearsonNewVsPeer,
        List<GapAlert> gapAlerts,
        LocalDateTime generatedAt
) {}
