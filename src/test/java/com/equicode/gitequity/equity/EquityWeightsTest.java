package com.equicode.gitequity.equity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EquityWeightsTest {

    // 기본값(0.75 / 0.15 / 0.10)과 동일하게 구성
    private final EquityWeights weights = new EquityWeights(0.75, 0.15, 0.10);

    @Test
    @DisplayName("기본 가중치를 [w_pr, w_review, w_comment] 배열로 반환한다")
    void defaults_returnsConfiguredWeights() {
        double[] result = weights.defaults();

        assertThat(result).containsExactly(0.75, 0.15, 0.10);
    }

    @Test
    @DisplayName("생성자로 가중치를 오버라이드하면 defaults()에 반영된다")
    void defaults_reflectsCustomWeights() {
        EquityWeights custom = new EquityWeights(0.5, 0.3, 0.2);

        assertThat(custom.defaults()).containsExactly(0.5, 0.3, 0.2);
    }
}
