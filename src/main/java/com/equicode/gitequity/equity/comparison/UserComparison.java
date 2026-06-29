package com.equicode.gitequity.equity.comparison;

/**
 * 사용자 1인의 legacy(1.0.0) vs 신규(2.0.0) 지분 비교
 *
 * rankChange = legacyRank - newRank (양수면 신규 공식에서 순위가 상승함을 의미)
 */
public record UserComparison(
        Long userId,
        String username,
        double legacyPercentage,
        double newPercentage,
        int legacyRank,
        int newRank,
        int rankChange
) {}
