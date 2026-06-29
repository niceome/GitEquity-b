package com.equicode.gitequity.equity.dmm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CommitDmmResultTest {

    private CommitDmmResult result(Double size, Double complexity, Double interfacing) {
        return new CommitDmmResult("abc123", "feat: x (#42)", size, complexity, interfacing, 42);
    }

    @Test
    @DisplayName("세 지표 모두 존재하면 평균을 반환해야 한다")
    void averageDmm_allPresent_returnsAverage() {
        CommitDmmResult dmm = result(1.0, 0.5, 0.0);

        assertThat(dmm.averageDmm()).contains(0.5);
    }

    @Test
    @DisplayName("일부 지표만 존재하면 존재하는 값들의 평균을 반환해야 한다")
    void averageDmm_partiallyPresent_averagesNonNullOnly() {
        CommitDmmResult dmm = result(1.0, null, null);

        assertThat(dmm.averageDmm()).contains(1.0);
    }

    @Test
    @DisplayName("세 지표 모두 null이면 empty를 반환해야 한다")
    void averageDmm_allNull_returnsEmpty() {
        CommitDmmResult dmm = result(null, null, null);

        assertThat(dmm.averageDmm()).isEmpty();
    }

    @Test
    @DisplayName("PR 번호가 없는 커밋도 record로 표현 가능해야 한다")
    void prNumber_canBeNull() {
        CommitDmmResult dmm = new CommitDmmResult("abc123", "chore: misc commit", 1.0, 1.0, 1.0, null);

        assertThat(dmm.prNumber()).isNull();
        assertThat(dmm.averageDmm()).contains(1.0);
    }
}
