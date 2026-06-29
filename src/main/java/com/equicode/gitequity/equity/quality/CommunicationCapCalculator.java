package com.equicode.gitequity.equity.quality;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * S6(커뮤니케이션 기여) 총점에 상한(cap)을 적용한다.
 *
 * cap = prScoreSum × capRatio
 * 결과 = min(rawTotal, cap)
 *
 * 기본 capRatio=0.1 (사용자 PR 점수 합의 10%). equity.communication.cap-ratio로 오버라이드 가능.
 */
@Component
public class CommunicationCapCalculator {

    private final double capRatio;

    public CommunicationCapCalculator(@Value("${equity.communication.cap-ratio:0.1}") double capRatio) {
        this.capRatio = capRatio;
    }

    public double apply(double rawTotal, double prScoreSum) {
        double cap = prScoreSum * capRatio;
        return Math.min(rawTotal, cap);
    }
}
