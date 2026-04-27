package com.equicode.gitequity.equity;

import com.equicode.gitequity.domain.ContributionType;

import java.util.Map;

/**
 * 사용자 1인의 최종 지분 계산 결과
 * percentage: 전체 대비 이 사용자의 지분 (소수점 2자리 %)
 */
public record UserEquity(
        Long userId,
        String username,
        double rawScore,
        double percentage,
        Map<ContributionType, Double> byType
) {}
