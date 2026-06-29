package com.equicode.gitequity.equity.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrTypeClassifierTest {

    private final PrTypeClassifier classifier = new PrTypeClassifier();

    // ── 1. 라벨 우선 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("라벨에 'feature'가 있으면 FEAT로 분류한다")
    void label_feature_returnsFeat() {
        PrType result = classifier.classify(List.of("feature"), "update something");

        assertThat(result).isEqualTo(PrType.FEAT);
    }

    @Test
    @DisplayName("라벨에 'bug'가 있으면 제목과 무관하게 FIX로 분류한다")
    void label_bug_overridesTitleHeuristic() {
        PrType result = classifier.classify(List.of("bug"), "implement new dashboard");

        assertThat(result).isEqualTo(PrType.FIX);
    }

    // ── 2. Conventional Commits prefix ───────────────────────────────────────

    @Test
    @DisplayName("'feat: ...' 제목은 FEAT로 분류한다")
    void title_feat_prefix_returnsFeat() {
        PrType result = classifier.classify(List.of(), "feat: add login page");

        assertThat(result).isEqualTo(PrType.FEAT);
    }

    @Test
    @DisplayName("'fix(auth): ...' 제목은 FIX로 분류한다")
    void title_fixScoped_prefix_returnsFix() {
        PrType result = classifier.classify(List.of(), "fix(auth): handle null token");

        assertThat(result).isEqualTo(PrType.FIX);
    }

    @Test
    @DisplayName("'docs: ...' 제목은 DOCS로 분류한다")
    void title_docs_prefix_returnsDocs() {
        PrType result = classifier.classify(List.of(), "docs: update API documentation");

        assertThat(result).isEqualTo(PrType.DOCS);
    }

    @Test
    @DisplayName("'Refactor ...' (대문자 시작) 제목은 REFACTOR로 분류한다")
    void title_refactorPrefix_caseInsensitive_returnsRefactor() {
        PrType result = classifier.classify(List.of(), "Refactor user service");

        assertThat(result).isEqualTo(PrType.REFACTOR);
    }

    // ── 3. 영어 키워드 휴리스틱 ───────────────────────────────────────────────

    @Test
    @DisplayName("'Implement ...' 제목은 FEAT로 분류한다")
    void title_implementKeyword_returnsFeat() {
        PrType result = classifier.classify(List.of(), "Implement payment gateway");

        assertThat(result).isEqualTo(PrType.FEAT);
    }

    @Test
    @DisplayName("'Resolve ...' 제목은 FIX로 분류한다")
    void title_resolveKeyword_returnsFix() {
        PrType result = classifier.classify(List.of(), "Resolve race condition in worker");

        assertThat(result).isEqualTo(PrType.FIX);
    }

    // ── 4. 한국어 키워드 휴리스틱 ─────────────────────────────────────────────

    @Test
    @DisplayName("'로그인 기능 구현' 제목은 FEAT로 분류한다")
    void title_koreanImplement_returnsFeat() {
        PrType result = classifier.classify(List.of(), "로그인 기능 구현");

        assertThat(result).isEqualTo(PrType.FEAT);
    }

    @Test
    @DisplayName("'토큰 만료 버그 수정' 제목은 FIX로 분류한다")
    void title_koreanBugFix_returnsFix() {
        PrType result = classifier.classify(List.of(), "토큰 만료 버그 수정");

        assertThat(result).isEqualTo(PrType.FIX);
    }

    @Test
    @DisplayName("'변수명 rename 및 포맷팅 정리' 제목은 STYLE로 분류한다")
    void title_koreanRenameFormat_returnsStyle() {
        PrType result = classifier.classify(List.of(), "변수명 rename 및 포맷팅 정리");

        assertThat(result).isEqualTo(PrType.STYLE);
    }

    @Test
    @DisplayName("'인증 모듈 리팩토링' 제목은 REFACTOR로 분류한다")
    void title_koreanRefactor_returnsRefactor() {
        PrType result = classifier.classify(List.of(), "인증 모듈 리팩토링");

        assertThat(result).isEqualTo(PrType.REFACTOR);
    }

    // ── 5. UNKNOWN ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("라벨/prefix/키워드 모두 매칭되지 않으면 UNKNOWN으로 분류한다")
    void title_noMatch_returnsUnknown() {
        PrType result = classifier.classify(List.of(), "Bump dependencies version");

        assertThat(result).isEqualTo(PrType.UNKNOWN);
    }

    @Test
    @DisplayName("라벨/제목이 비어 있으면 UNKNOWN으로 분류한다")
    void emptyLabelsAndTitle_returnsUnknown() {
        PrType result = classifier.classify(List.of(), "");

        assertThat(result).isEqualTo(PrType.UNKNOWN);
    }
}
