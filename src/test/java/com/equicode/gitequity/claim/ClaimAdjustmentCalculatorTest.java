package com.equicode.gitequity.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ClaimAdjustmentCalculatorTest {

    private final ClaimAdjustmentCalculator calculator = new ClaimAdjustmentCalculator(0.05);

    @Test
    @DisplayName("weightedScore가 0이면 multiplier는 1.0이다")
    void zeroScore_returnsOne() {
        assertThat(calculator.multiplier(0.0)).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("multiplier = 1 + weightedScore × scoreScale")
    void positiveScore_appliesScale() {
        assertThat(calculator.multiplier(1.0)).isCloseTo(1.05, within(0.0001));
    }

    @Test
    @DisplayName("multiplier는 최대 1.1(+10%)로 클램프된다")
    void largeScore_clampedToMax() {
        assertThat(calculator.multiplier(10.0)).isCloseTo(1.1, within(0.0001));
    }

    @Test
    @DisplayName("multiplier는 최소 0.9(-10%)로 클램프된다")
    void largeNegativeScore_clampedToMin() {
        assertThat(calculator.multiplier(-10.0)).isCloseTo(0.9, within(0.0001));
    }
}
