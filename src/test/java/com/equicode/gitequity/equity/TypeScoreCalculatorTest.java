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
    @DisplayName("5명 전체 합산 점수가 230.5 이어야 한다")
    void totalScore_shouldBe230_5() {
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
    @DisplayName("기본 가중치에서 PR 위주인 Alice가 1위여야 한다")
    void ranking_aliceShouldBeFirst_withDefaultWeights() {
        var scores = calculator.calculate(allContributions(), null);

        UserScore top = scores.values().stream()
                .max(java.util.Comparator.comparingDouble(UserScore::total))
                .orElseThrow();

        assertThat(top.username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("기본 가중치에서 순위는 Alice > Bob > Dave > Carol > Eve 순이어야 한다")
    void ranking_shouldBeInCorrectOrder_withDefaultWeights() {
        var scores = calculator.calculate(allContributions(), null);

        List<String> ranked = scores.values().stream()
                .sorted(java.util.Comparator.comparingDouble(UserScore::total).reversed())
                .map(UserScore::username)
                .toList();

        assertThat(ranked).containsExactly("alice", "bob", "dave", "carol", "eve");
    }

    // ── 4. 커스텀 가중치 변경 시 결과 변화 ────────────────────────────────────

    @Test
    @DisplayName("PR 가중치를 0으로 낮추면 Alice가 1위에서 탈락해야 한다")
    void customWeight_prZero_aliceShouldNotBeFirst() {
        Map<String, Double> customWeights = Map.of("PR", 0.0);
        var scores = calculator.calculate(allContributions(), customWeights);

        UserScore top = scores.values().stream()
                .max(java.util.Comparator.comparingDouble(UserScore::total))
                .orElseThrow();

        assertThat(top.username()).isNotEqualTo("alice");
    }

    @Test
    @DisplayName("PR 가중치를 0으로 낮추면 Commit 위주 Bob이 1위가 되어야 한다")
    void customWeight_prZero_bobShouldBeFirst() {
        Map<String, Double> customWeights = Map.of("PR", 0.0);
        var scores = calculator.calculate(allContributions(), customWeights);

        UserScore top = scores.values().stream()
                .max(java.util.Comparator.comparingDouble(UserScore::total))
                .orElseThrow();

        assertThat(top.username()).isEqualTo("bob");
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
    @DisplayName("Alice의 PR 점수는 60.0 이어야 한다 (20건 × 3.0)")
    void byType_alicePrScore_shouldBe60() {
        var scores = calculator.calculate(aliceContributions(), null);

        double prScore = scores.get(ALICE.getId()).byType()
                .getOrDefault(ContributionType.PR, 0.0);

        assertThat(prScore).isCloseTo(60.0, within(0.001));
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
