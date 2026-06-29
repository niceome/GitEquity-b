package com.equicode.gitequity.equity.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CommunicationCapCalculatorTest {

    // 기본값(capRatio=0.1)과 동일하게 구성
    private final CommunicationCapCalculator calculator = new CommunicationCapCalculator(0.1);

    @Test
    @DisplayName("raw 총점이 cap(PR점수합×10%)보다 작으면 그대로 반환한다")
    void rawBelowCap_returnsRaw() {
        double result = calculator.apply(5.0, 100.0);

        assertThat(result).isCloseTo(5.0, within(0.0001));
    }

    @Test
    @DisplayName("raw 총점이 cap을 초과하면 cap으로 제한한다")
    void rawAboveCap_clampsToCap() {
        double result = calculator.apply(50.0, 100.0);

        assertThat(result).isCloseTo(10.0, within(0.0001));
    }

    @Test
    @DisplayName("PR 점수 합이 0이면 cap도 0이 되어 커뮤니케이션 점수가 0이 된다")
    void zeroPrScore_yieldsZeroCap() {
        double result = calculator.apply(5.0, 0.0);

        assertThat(result).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName("capRatio를 오버라이드하면 그에 맞게 cap이 변한다")
    void customCapRatio_isApplied() {
        CommunicationCapCalculator custom = new CommunicationCapCalculator(0.2);

        double result = custom.apply(50.0, 100.0);

        assertThat(result).isCloseTo(20.0, within(0.0001));
    }
}
