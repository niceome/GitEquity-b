package com.equicode.gitequity.equity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ExplorationCapCalculatorTest {

    // 기본값(capRatio=0.15)과 동일하게 구성
    private final ExplorationCapCalculator calculator = new ExplorationCapCalculator(0.15);

    @Test
    @DisplayName("탐색 점수 합이 cap(merged 점수합×15%)보다 작으면 그대로 반환한다")
    void rawBelowCap_returnsRaw() {
        double result = calculator.apply(5.0, 100.0);

        assertThat(result).isCloseTo(5.0, within(0.0001));
    }

    @Test
    @DisplayName("탐색 점수 합이 cap을 초과하면 cap으로 제한한다")
    void rawAboveCap_clampsToCap() {
        double result = calculator.apply(50.0, 100.0);

        assertThat(result).isCloseTo(15.0, within(0.0001));
    }

    @Test
    @DisplayName("merged 점수 합이 0이면 cap도 0이 되어 탐색 점수가 0이 된다")
    void zeroMergedScore_yieldsZeroCap() {
        double result = calculator.apply(5.0, 0.0);

        assertThat(result).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName("capRatio를 오버라이드하면 그에 맞게 cap이 변한다")
    void customCapRatio_isApplied() {
        ExplorationCapCalculator custom = new ExplorationCapCalculator(0.5);

        double result = custom.apply(80.0, 100.0);

        assertThat(result).isCloseTo(50.0, within(0.0001));
    }
}
