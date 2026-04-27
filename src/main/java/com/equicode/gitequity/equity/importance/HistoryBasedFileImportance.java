package com.equicode.gitequity.equity.importance;

import org.springframework.stereotype.Component;

/**
 * 변경 이력 통계를 0.5~1.5 범위의 중요도 승수로 정규화한다.
 *
 * 공식:
 *   raw   = changeCount × 0.5 + contributorCount × 1.0 + reviewCommentCount × 0.3
 *   score = BASE + clamp(raw / THRESHOLD, 0.0, 1.0)
 *
 *   BASE      = 0.5 (최솟값 보장)
 *   THRESHOLD = 20.0 (raw ≥ 20 이면 score = 1.5)
 *
 * 예시:
 *   changeCount=10, contributorCount=5, reviewCommentCount=0
 *     raw=10.0  → score = 0.5 + 0.5 = 1.0
 *   changeCount=30, contributorCount=10, reviewCommentCount=5
 *     raw=26.5  → clamp(26.5/20, 0, 1) = 1.0  → score = 1.5
 */
@Component
public class HistoryBasedFileImportance {

    private static final double BASE      = 0.5;
    private static final double THRESHOLD = 20.0;

    public double score(FileHistoryStats stats) {
        double raw = stats.changeCount() * 0.5
                   + stats.contributorCount() * 1.0
                   + stats.reviewCommentCount() * 0.3;

        double normalized = Math.min(1.0, raw / THRESHOLD);
        return BASE + normalized;  // 0.5 ~ 1.5
    }
}
