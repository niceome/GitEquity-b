package com.equicode.gitequity.equity.comparison;

/**
 * 신규 알고리즘(2.0.0) 순위와 동료 평가 순위 간 괴리 알림
 *
 * |algorithmRank - peerRank| >= 2인 사용자에 대해 생성된다.
 */
public record GapAlert(
        Long userId,
        String username,
        int algorithmRank,
        int peerRank,
        int rankDiff,
        String message
) {}
