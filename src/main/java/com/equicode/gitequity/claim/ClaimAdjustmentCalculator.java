package com.equicode.gitequity.claim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 확정된 비코드 기여 인정 요청(claim)의 유형별 가중치 점수를 보정 배수(multiplier)로 변환한다.
 *
 * multiplier = clamp(1 + weightedScore × scoreScale, 0.9, 1.1)
 *
 * 기본 scoreScale=0.05 (가중치 점수 1점당 ±5%p 보정). equity.claim.score-scale로 오버라이드 가능.
 * 담합(self-dealing) 인센티브를 구조적으로 제한하기 위해 보정 배수는 ±10%로 클램프된다.
 */
@Component
public class ClaimAdjustmentCalculator {

    public static final double MIN_MULTIPLIER = 0.9;
    public static final double MAX_MULTIPLIER = 1.1;

    private final double scoreScale;

    public ClaimAdjustmentCalculator(@Value("${equity.claim.score-scale:0.05}") double scoreScale) {
        this.scoreScale = scoreScale;
    }

    public double multiplier(double weightedScore) {
        double raw = 1.0 + weightedScore * scoreScale;
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, raw));
    }
}
