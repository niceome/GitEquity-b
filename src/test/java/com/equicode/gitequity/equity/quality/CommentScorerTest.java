package com.equicode.gitequity.equity.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CommentScorerTest {

    private final CommentScorer scorer = new CommentScorer();

    @Test
    @DisplayName("30자 미만 코멘트는 0.1점이어야 한다")
    void shortComment_yieldsMinimumScore() {
        double score = scorer.score("ok thanks!");

        assertThat(score).isCloseTo(0.1, within(0.0001));
    }

    @Test
    @DisplayName("150자 코멘트는 길이/300=0.5점이어야 한다")
    void midLengthComment_yieldsLengthBasedScore() {
        String body = "a".repeat(150);

        double score = scorer.score(body);

        assertThat(score).isCloseTo(0.5, within(0.0001));
    }

    @Test
    @DisplayName("300자 이상 코멘트는 최대 1.0점으로 clamp된다")
    void veryLongComment_isClampedToOne() {
        String body = "a".repeat(600);

        double score = scorer.score(body);

        assertThat(score).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("코드블록이 포함되면 0.5가 가산된다")
    void codeBlock_addsBonus() {
        // 길이 기반 점수가 둘 다 1.0으로 clamp되도록 300자 이상 사용 → 차이는 코드블록 보너스만 반영
        String body = "a".repeat(300);
        String bodyWithCode = body + "\n```\ncode\n```";

        double without = scorer.score(body);
        double with = scorer.score(bodyWithCode);

        assertThat(with - without).isCloseTo(0.5, within(0.0001));
    }

    @Test
    @DisplayName("null 본문은 0.1점을 반환해야 한다")
    void nullBody_returnsMinimumScore() {
        assertThat(scorer.score(null)).isCloseTo(0.1, within(0.0001));
    }
}
