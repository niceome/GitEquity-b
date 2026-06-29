package com.equicode.gitequity.equity.quality;

import com.equicode.gitequity.equity.quality.CoAuthorParser.CoAuthor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CoAuthorParserTest {

    private final CoAuthorParser parser = new CoAuthorParser();

    // ── PR 번호 추출 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("메시지 첫 줄 끝의 (#123)에서 PR 번호를 추출한다")
    void extractPrNumber_fromFirstLine() {
        String message = "feat: add login page (#123)\n\nCo-authored-by: Bob <bob@example.com>";

        Integer prNumber = parser.extractPrNumber(message);

        assertThat(prNumber).isEqualTo(123);
    }

    @Test
    @DisplayName("PR 번호가 없는 메시지는 null을 반환한다")
    void extractPrNumber_noMatch_returnsNull() {
        Integer prNumber = parser.extractPrNumber("fix: typo in README");

        assertThat(prNumber).isNull();
    }

    @Test
    @DisplayName("null 메시지는 null을 반환한다")
    void extractPrNumber_nullMessage_returnsNull() {
        assertThat(parser.extractPrNumber(null)).isNull();
    }

    // ── Co-authored-by 추출 ───────────────────────────────────────────────────

    @Test
    @DisplayName("Co-authored-by 트레일러에서 이름과 이메일을 추출한다")
    void extractCoAuthors_singleTrailer() {
        String message = "feat: add login page (#123)\n\n" +
                "Co-authored-by: Bob Lee <bob@example.com>";

        List<CoAuthor> coAuthors = parser.extractCoAuthors(message);

        assertThat(coAuthors).hasSize(1);
        assertThat(coAuthors.get(0).name()).isEqualTo("Bob Lee");
        assertThat(coAuthors.get(0).email()).isEqualTo("bob@example.com");
    }

    @Test
    @DisplayName("여러 Co-authored-by 트레일러를 모두 추출한다")
    void extractCoAuthors_multipleTrailers() {
        String message = "feat: add login page (#123)\n\n" +
                "Co-authored-by: Bob Lee <bob@example.com>\n" +
                "Co-authored-by: Carol Kim <carol@example.com>";

        List<CoAuthor> coAuthors = parser.extractCoAuthors(message);

        assertThat(coAuthors).hasSize(2);
        assertThat(coAuthors).extracting(CoAuthor::email)
                .containsExactly("bob@example.com", "carol@example.com");
    }

    @Test
    @DisplayName("Co-authored-by가 없으면 빈 목록을 반환한다")
    void extractCoAuthors_noTrailer_returnsEmpty() {
        String message = "feat: add login page (#123)\n\nno trailers here";

        assertThat(parser.extractCoAuthors(message)).isEmpty();
    }

    @Test
    @DisplayName("null 메시지는 빈 목록을 반환한다")
    void extractCoAuthors_nullMessage_returnsEmpty() {
        assertThat(parser.extractCoAuthors(null)).isEmpty();
    }
}
