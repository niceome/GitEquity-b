package com.equicode.gitequity.equity.comparison;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PearsonCorrelationTest {

    @Test
    @DisplayName("완전한 양의 선형관계는 상관계수 1.0이다")
    void perfectPositiveCorrelation_isOne() {
        double[] x = {1, 2, 3, 4, 5};
        double[] y = {2, 4, 6, 8, 10};

        Double result = PearsonCorrelation.compute(x, y);

        assertThat(result).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("완전한 음의 선형관계는 상관계수 -1.0이다")
    void perfectNegativeCorrelation_isMinusOne() {
        double[] x = {1, 2, 3, 4, 5};
        double[] y = {10, 8, 6, 4, 2};

        Double result = PearsonCorrelation.compute(x, y);

        assertThat(result).isCloseTo(-1.0, within(0.0001));
    }

    @Test
    @DisplayName("표본이 2개 미만이면 null을 반환한다")
    void insufficientSamples_returnsNull() {
        double[] x = {1};
        double[] y = {2};

        assertThat(PearsonCorrelation.compute(x, y)).isNull();
    }

    @Test
    @DisplayName("한쪽의 분산이 0이면(모든 값이 동일) null을 반환한다")
    void zeroVariance_returnsNull() {
        double[] x = {1, 1, 1};
        double[] y = {1, 2, 3};

        assertThat(PearsonCorrelation.compute(x, y)).isNull();
    }

    @Test
    @DisplayName("길이가 다른 배열은 null을 반환한다")
    void mismatchedLength_returnsNull() {
        double[] x = {1, 2, 3};
        double[] y = {1, 2};

        assertThat(PearsonCorrelation.compute(x, y)).isNull();
    }
}
