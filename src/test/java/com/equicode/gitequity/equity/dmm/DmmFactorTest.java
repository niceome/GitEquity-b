package com.equicode.gitequity.equity.dmm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DmmFactorTest {

    // application 기본값(factorBase=0.7, factorScale=0.6)과 동일하게 구성
    private final DmmFactor factor = new DmmFactor(0.7, 0.6);

    @Test
    @DisplayName("dmm=null이면 중립값 1.0을 반환해야 한다")
    void factor_nullDmm_returnsNeutral() {
        assertThat(factor.factor(null)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("dmm=0.0이면 factorBase(0.7)를 반환해야 한다")
    void factor_zeroDmm_returnsFactorBase() {
        assertThat(factor.factor(0.0)).isCloseTo(0.7, within(0.0001));
    }

    @Test
    @DisplayName("dmm=1.0이면 factorBase+factorScale(1.3)을 반환해야 한다")
    void factor_oneDmm_returnsUpperBound() {
        assertThat(factor.factor(1.0)).isCloseTo(1.3, within(0.0001));
    }

    @Test
    @DisplayName("dmm=0.5이면 0.7과 1.3 사이 중간값(1.0)을 반환해야 한다")
    void factor_midDmm_returnsMidpoint() {
        assertThat(factor.factor(0.5)).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("factorBase/factorScale을 변경하면 결과도 그에 맞게 바뀌어야 한다 (설정 오버라이드 가능)")
    void factor_customConstants_areApplied() {
        DmmFactor custom = new DmmFactor(0.5, 1.0);

        assertThat(custom.factor(0.0)).isCloseTo(0.5, within(0.0001));
        assertThat(custom.factor(1.0)).isCloseTo(1.5, within(0.0001));
    }
}
