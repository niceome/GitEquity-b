package com.equicode.gitequity.equity.dmm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DMM(Delta Maintainability Model) 점수를 기여 점수 보정 계수로 변환한다.
 *
 * factor = dmm == null ? 1.0(중립) : factorBase + factorScale * dmm
 *
 * 기본값(factorBase=0.7, factorScale=0.6) 기준 dmm ∈ [0, 1] → factor ∈ [0.7, 1.3]
 */
@Component
public class DmmFactor {

    private final double factorBase;
    private final double factorScale;

    public DmmFactor(
            @Value("${equity.dmm.factor-base:0.7}") double factorBase,
            @Value("${equity.dmm.factor-scale:0.6}") double factorScale) {
        this.factorBase = factorBase;
        this.factorScale = factorScale;
    }

    public double factor(Double dmm) {
        return dmm == null ? 1.0 : factorBase + factorScale * dmm;
    }
}
