package com.equicode.gitequity.equity.importance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 경로 기반(60%) + 이력 기반(40%) 점수를 결합하여 최종 fileImportance를 반환한다.
 *
 * 최종 범위: clamp(combined, 0.3, 2.5)
 *   - 경로 기반: 0.5 ~ 2.0
 *   - 이력 기반: 0.5 ~ 1.5
 *   - 결합 범위: 0.5×0.6 + 0.5×0.4 = 0.5  ~  2.0×0.6 + 1.5×0.4 = 1.8
 */
@Component
@RequiredArgsConstructor
public class FileImportanceCalculator {

    private static final double PATH_WEIGHT    = 0.6;
    private static final double HISTORY_WEIGHT = 0.4;
    private static final double MIN_SCORE      = 0.3;
    private static final double MAX_SCORE      = 2.5;

    private final PathBasedFileImportance    pathBased;
    private final HistoryBasedFileImportance historyBased;

    /**
     * 파일 경로와 이력 통계를 결합하여 fileImportance 승수를 반환한다.
     */
    public double calculate(String filepath, FileHistoryStats stats) {
        double pathScore    = pathBased.scoreOf(filepath);
        double historyScore = historyBased.score(stats);
        double combined     = pathScore * PATH_WEIGHT + historyScore * HISTORY_WEIGHT;
        return Math.min(MAX_SCORE, Math.max(MIN_SCORE, combined));
    }

    /**
     * 이력 데이터 없이 경로만으로 계산 (history = empty stats → score=0.5)
     */
    public double calculatePathOnly(String filepath) {
        return calculate(filepath, FileHistoryStats.empty());
    }
}
