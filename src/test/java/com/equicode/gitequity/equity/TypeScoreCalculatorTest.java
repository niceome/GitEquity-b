package com.equicode.gitequity.equity;

import com.equicode.gitequity.domain.ContributionType;
import com.equicode.gitequity.fixture.ContributionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.equicode.gitequity.fixture.ContributionFixture.*;
import static org.assertj.core.api.Assertions.*;

class TypeScoreCalculatorTest {

    private TypeScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TypeScoreCalculator();
    }

    // ── 1. 합산 점수 검증 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("5명 전체 합산 점수가 46.5 이어야 한다 (REVIEW+ISSUE만 반영)")
    void totalScore_shouldBe46_5() {
        var scores = calculator.calculate(allContributions(), null);

        double grandTotal = scores.values().stream()
                .mapToDouble(UserScore::total).sum();

        assertThat(grandTotal).isCloseTo(TOTAL_EXPECTED, within(0.001));
    }

    @Test
    @DisplayName("각 사용자 점수가 기대값과 일치해야 한다")
    void perUserScore_shouldMatchExpected() {
        var scores = calculator.calculate(allContributions(), null);

        assertThat(scores.get(ALICE.getId()).total()).isCloseTo(ALICE_EXPECTED, within(0.001));
        assertThat(scores.get(BOB.getId()).total())  .isCloseTo(BOB_EXPECTED,   within(0.001));
        assertThat(scores.get(CAROL.getId()).total()).isCloseTo(CAROL_EXPECTED,  within(0.001));
        assertThat(scores.get(DAVE.getId()).total()) .isCloseTo(DAVE_EXPECTED,   within(0.001));
        assertThat(scores.get(EVE.getId()).total())  .isCloseTo(EVE_EXPECTED,    within(0.001));
    }

    // ── 2. 지분 퍼센트 합 = 100% ──────────────────────────────────────────────

    @Test
    @DisplayName("모든 사용자의 지분 비율 합이 정확히 100%여야 한다")
    void equityPercent_shouldSumTo100() {
        var scores = calculator.calculate(allContributions(), null);
        double grandTotal = scores.values().stream().mapToDouble(UserScore::total).sum();

        double percentSum = scores.values().stream()
                .mapToDouble(s -> s.equityPercent(grandTotal))
                .sum();

        assertThat(percentSum).isCloseTo(100.0, within(0.01));
    }

    // ── 3. 순위 검증 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("기본 가중치에서 Review 위주인 Carol이 1위여야 한다")
    void ranking_carolShouldBeFirst_withDefaultWeights() {
        var scores = calculator.calculate(allContributions(), null);

        UserScore top = scores.values().stream()
                .max(java.util.Comparator.comparingDouble(UserScore::total))
                .orElseThrow();

        assertThat(top.username()).isEqualTo("carol");
    }

    @Test
    @DisplayName("기본 가중치에서 순위는 Carol > Eve > Dave > Bob > Alice 순이어야 한다")
    void ranking_shouldBeInCorrectOrder_withDefaultWeights() {
        var scores = calculator.calculate(allContributions(), null);

        List<String> ranked = scores.values().stream()
                .sorted(java.util.Comparator.comparingDouble(UserScore::total).reversed())
                .map(UserScore::username)
                .toList();

        assertThat(ranked).containsExactly("carol", "eve", "dave", "bob", "alice");
    }

    // ── 4. 커스텀 가중치 변경 시 결과 변화 ────────────────────────────────────

    @Test
    @DisplayName("PR 가중치를 변경해도 총점은 변하지 않아야 한다 (PR은 집계 대상 제외)")
    void customWeight_pr_hasNoEffectOnTotal() {
        var defaultScores = calculator.calculate(allContributions(), null);
        double defaultTotal = defaultScores.values().stream().mapToDouble(UserScore::total).sum();

        var customScores = calculator.calculate(allContributions(), Map.of("PR", 100.0));
        double customTotal = customScores.values().stream().mapToDouble(UserScore::total).sum();

        assertThat(customTotal).isCloseTo(defaultTotal, within(0.001));
    }

    @Test
    @DisplayName("COMMIT 가중치를 변경해도 총점은 변하지 않아야 한다 (COMMIT은 집계 대상 제외)")
    void customWeight_commit_hasNoEffectOnTotal() {
        var defaultScores = calculator.calculate(allContributions(), null);
        double defaultTotal = defaultScores.values().stream().mapToDouble(UserScore::total).sum();

        var customScores = calculator.calculate(allContributions(), Map.of("COMMIT", 100.0));
        double customTotal = customScores.values().stream().mapToDouble(UserScore::total).sum();

        assertThat(customTotal).isCloseTo(defaultTotal, within(0.001));
    }

    @Test
    @DisplayName("Issue 가중치를 10.0으로 높이면 Issue 위주 Eve가 1위가 되어야 한다")
    void customWeight_issueHigh_eveShouldBeFirst() {
        Map<String, Double> customWeights = Map.of("ISSUE", 10.0);
        var scores = calculator.calculate(allContributions(), customWeights);

        UserScore top = scores.values().stream()
                .max(java.util.Comparator.comparingDouble(UserScore::total))
                .orElseThrow();

        assertThat(top.username()).isEqualTo("eve");
    }

    // ── 5. byType breakdown 검증 ──────────────────────────────────────────────

    @Test
    @DisplayName("PR/COMMIT 기여는 byType 집계에서 제외되어야 한다")
    void byType_prAndCommit_excludedFromAggregation() {
        var scores = calculator.calculate(aliceContributions(), null);

        var byType = scores.get(ALICE.getId()).byType();

        assertThat(byType).doesNotContainKey(ContributionType.PR);
        assertThat(byType).doesNotContainKey(ContributionType.COMMIT);
        assertThat(byType).containsKeys(ContributionType.REVIEW, ContributionType.ISSUE);
    }

    // ── 6. 빈 목록 처리 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("기여 목록이 비어 있으면 빈 맵을 반환해야 한다")
    void emptyContributions_shouldReturnEmptyMap() {
        var scores = calculator.calculate(List.of(), null);
        assertThat(scores).isEmpty();
    }

    // ── 7. 성능 테스트 (100명 × 100건) ────────────────────────────────────────

    @Test
    @DisplayName("100명 × 100건(10,000건) 계산이 100ms 이내에 완료되어야 한다")
    void performance_100users_100contributions_under100ms() {
        var data = ContributionFixture.generateForPerformanceTest(100, 100);

        long start = System.currentTimeMillis();
        var scores = calculator.calculate(data, null);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(scores).hasSize(100);
        assertThat(elapsed).isLessThan(100L);
    }
}
