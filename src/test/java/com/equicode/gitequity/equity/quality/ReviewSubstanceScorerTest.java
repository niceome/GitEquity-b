package com.equicode.gitequity.equity.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ReviewSubstanceScorerTest {

    private final ReviewSubstanceScorer scorer = new ReviewSubstanceScorer();

    // ── 1. 완료 기준: "LGTM" 한 줄 리뷰 vs 200자+코드블록 리뷰 ────────────────────

    @Test
    @DisplayName("\"LGTM\" 한 줄 APPROVED 리뷰는 base×substance가 작은 값(0.08)이어야 한다")
    void lgtmApproval_yieldsLowScore() {
        double score = scorer.score("APPROVED", "LGTM");

        // base(APPROVED)=0.4 × substance(<20자)=0.2 = 0.08
        assertThat(score).isCloseTo(0.08, within(0.0001));
    }

    @Test
    @DisplayName("200자 초과 + 코드블록 포함 CHANGES_REQUESTED 리뷰는 1.6점이어야 한다")
    void longCodeBlockChangesRequested_yieldsHighScore() {
        String longBody = "이 부분은 좀 더 명확한 변수명이 필요합니다. ".repeat(10) + "```java\nint x = 1;\n```";

        double score = scorer.score("CHANGES_REQUESTED", longBody);

        // base(CHANGES_REQUESTED)=1.0 × substance(>200자)=1.3 + codeBlock(0.3) = 1.6
        assertThat(score).isCloseTo(1.6, within(0.0001));
    }

    @Test
    @DisplayName("LGTM과 200자+코드블록 리뷰의 점수는 유의미하게(20배 이상) 차이나야 한다")
    void lgtmVsSubstantialReview_significantlyDifferent() {
        String longBody = "이 부분은 좀 더 명확한 변수명이 필요합니다. ".repeat(10) + "```java\nint x = 1;\n```";

        double lgtmScore = scorer.score("APPROVED", "LGTM");
        double substantialScore = scorer.score("CHANGES_REQUESTED", longBody);

        // 0.08 vs 1.6 = 20배 (부동소수점 오차 감안하여 19.99 이상으로 검증)
        assertThat(substantialScore / lgtmScore).isGreaterThanOrEqualTo(19.99);
    }

    // ── 2. base score by state ────────────────────────────────────────────────

    @Test
    @DisplayName("CHANGES_REQUESTED의 base는 1.0이어야 한다")
    void changesRequested_baseIsOne() {
        // substance=1.0이 되도록 20~200자 사이 본문 사용
        String body = "a".repeat(50);

        double score = scorer.score("CHANGES_REQUESTED", body);

        assertThat(score).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("COMMENTED의 base는 0.5이어야 한다")
    void commented_baseIsHalf() {
        String body = "a".repeat(50);

        double score = scorer.score("COMMENTED", body);

        assertThat(score).isCloseTo(0.5, within(0.0001));
    }

    @Test
    @DisplayName("APPROVED의 base는 0.4이어야 한다")
    void approved_baseIsPointFour() {
        String body = "a".repeat(50);

        double score = scorer.score("APPROVED", body);

        assertThat(score).isCloseTo(0.4, within(0.0001));
    }

    @Test
    @DisplayName("DISMISSED 등 기타 상태의 base는 0.2이어야 한다")
    void otherState_baseIsPointTwo() {
        String body = "a".repeat(50);

        double score = scorer.score("DISMISSED", body);

        assertThat(score).isCloseTo(0.2, within(0.0001));
    }

    // ── 3. substance multiplier by length ────────────────────────────────────

    @Test
    @DisplayName("20자 미만 본문은 substance 0.2배가 적용된다")
    void shortBody_substanceIsPointTwo() {
        double score = scorer.score("COMMENTED", "short");

        // base(0.5) × substance(0.2) = 0.1
        assertThat(score).isCloseTo(0.1, within(0.0001));
    }

    @Test
    @DisplayName("200자 초과 본문은 substance 1.3배가 적용된다")
    void longBody_substanceIsOnePointThree() {
        String body = "a".repeat(201);

        double score = scorer.score("COMMENTED", body);

        // base(0.5) × substance(1.3) = 0.65
        assertThat(score).isCloseTo(0.65, within(0.0001));
    }

    @Test
    @DisplayName("20~200자 사이 본문은 substance 1.0배가 적용된다")
    void midLengthBody_substanceIsOne() {
        String body = "a".repeat(100);

        double score = scorer.score("COMMENTED", body);

        // base(0.5) × substance(1.0) = 0.5
        assertThat(score).isCloseTo(0.5, within(0.0001));
    }

    // ── 4. 코드블록 보너스 ────────────────────────────────────────────────────

    @Test
    @DisplayName("코드블록이 포함되면 0.3이 가산된다")
    void codeBlock_addsBonus() {
        String body = "a".repeat(50);
        String bodyWithCode = body + "\n```\ncode\n```";

        double without = scorer.score("COMMENTED", body);
        double with = scorer.score("COMMENTED", bodyWithCode);

        assertThat(with - without).isCloseTo(0.3, within(0.0001));
    }

    // ── 5. null/빈 입력 처리 ──────────────────────────────────────────────────

    @Test
    @DisplayName("null 본문과 null state는 예외 없이 최소 점수를 반환해야 한다")
    void nullInputs_handledGracefully() {
        double score = scorer.score(null, null);

        // base(default)=0.2 × substance(<20자)=0.2 = 0.04
        assertThat(score).isCloseTo(0.04, within(0.0001));
    }
}
