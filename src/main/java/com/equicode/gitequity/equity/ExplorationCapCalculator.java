package com.equicode.gitequity.equity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * closed-unmerged PR("탐색 기여", S11)의 사용자별 점수 합에 상한(cap)을 적용한다.
 *
 * cap = mergedScoreSum × capRatio
 * 결과 = min(explorationRawSum, cap)
 *
 * 기본 capRatio=0.15 (본인 merged PR 점수 합의 15%). equity.exploration.cap-ratio로 오버라이드 가능.
 */
@Component
public class ExplorationCapCalculator {

    private final double capRatio;

    public ExplorationCapCalculator(@Value("${equity.exploration.cap-ratio:0.15}") double capRatio) {
        this.capRatio = capRatio;
    }

    public double apply(double explorationRawSum, double mergedScoreSum) {
        double cap = mergedScoreSum * capRatio;
        return Math.min(explorationRawSum, cap);
    }
}
