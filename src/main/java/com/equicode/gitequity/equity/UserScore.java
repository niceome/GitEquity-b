package com.equicode.gitequity.equity;

import com.equicode.gitequity.domain.ContributionType;

import java.util.Map;

/**
 * 사용자 1인의 기여 점수 집계 결과
 * byType: 기여 유형별 점수 breakdown
 * total : 전 유형 합산 점수 (지분 비율 계산 기준)
 */
public record UserScore(
        Long userId,
        String username,
        Map<ContributionType, Double> byType,
        double total
) {
    /** 전체 합 대비 이 사용자의 지분 비율 (0~1) */
    public double equityRatio(double grandTotal) {
        if (grandTotal == 0) return 0;
        return total / grandTotal;
    }

    /** 지분 퍼센트 (소수점 2자리) */
    public double equityPercent(double grandTotal) {
        return Math.round(equityRatio(grandTotal) * 10_000.0) / 100.0;
    }
}
