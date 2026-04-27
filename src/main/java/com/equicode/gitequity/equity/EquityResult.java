package com.equicode.gitequity.equity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 프로젝트 전체 지분 계산 결과 (스냅샷 저장 전 in-memory 결과)
 * equities: 지분율 내림차순 정렬
 */
public record EquityResult(
        Long projectId,
        List<UserEquity> equities,
        double totalRawScore,
        LocalDateTime calculatedAt
) {
    /** 특정 사용자의 지분 조회 */
    public UserEquity forUser(Long userId) {
        return equities.stream()
                .filter(e -> e.userId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    /** 지분 합계 검증 (부동소수점 오차 허용) */
    public boolean isSumValid() {
        double sum = equities.stream().mapToDouble(UserEquity::percentage).sum();
        return Math.abs(sum - 100.0) < 0.1;
    }
}
