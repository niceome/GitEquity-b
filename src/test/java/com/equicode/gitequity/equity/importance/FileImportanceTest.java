package com.equicode.gitequity.equity.importance;

import com.equicode.gitequity.equity.TypeScoreCalculator;
import com.equicode.gitequity.equity.UserScore;
import com.equicode.gitequity.fixture.ContributionFixture;
import com.equicode.gitequity.domain.Contribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class FileImportanceTest {

    private PathBasedFileImportance    pathBased;
    private HistoryBasedFileImportance historyBased;
    private FileImportanceCalculator   calculator;
    private TypeScoreCalculator        typeScoreCalculator;

    @BeforeEach
    void setUp() {
        pathBased           = new PathBasedFileImportance();
        historyBased        = new HistoryBasedFileImportance();
        calculator          = new FileImportanceCalculator(pathBased, historyBased);
        typeScoreCalculator = new TypeScoreCalculator();
    }

    // ────────────────────────────────────────────────────────────────────────
    // 1. PathBasedFileImportance — 경로 패턴 분류
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PathBasedFileImportance")
    class PathBasedTests {

        @Test
        @DisplayName("auth 경로 파일은 CRITICAL이어야 한다")
        void authPath_isCritical() {
            assertThat(pathBased.classify("src/main/java/com/example/auth/JwtTokenProvider.java"))
                    .isEqualTo(FileImportance.CRITICAL);
        }

        @Test
        @DisplayName("security 경로 파일은 CRITICAL이어야 한다")
        void securityPath_isCritical() {
            assertThat(pathBased.classify("src/main/java/com/example/security/SecurityConfig.java"))
                    .isEqualTo(FileImportance.CRITICAL);
        }

        @Test
        @DisplayName("payment 경로 파일은 CRITICAL이어야 한다")
        void paymentPath_isCritical() {
            assertThat(pathBased.classify("src/main/java/com/example/payment/PaymentService.java"))
                    .isEqualTo(FileImportance.CRITICAL);
        }

        @Test
        @DisplayName("service 경로 파일은 HIGH이어야 한다")
        void servicePath_isHigh() {
            assertThat(pathBased.classify("src/main/java/com/example/service/UserService.java"))
                    .isEqualTo(FileImportance.HIGH);
        }

        @Test
        @DisplayName("controller 경로 파일은 HIGH이어야 한다")
        void controllerPath_isHigh() {
            assertThat(pathBased.classify("src/main/java/com/example/controller/UserController.java"))
                    .isEqualTo(FileImportance.HIGH);
        }

        @Test
        @DisplayName("domain 경로 파일은 HIGH이어야 한다")
        void domainPath_isHigh() {
            assertThat(pathBased.classify("src/main/java/com/example/domain/User.java"))
                    .isEqualTo(FileImportance.HIGH);
        }

        @Test
        @DisplayName("test 경로 파일은 LOW이어야 한다")
        void testPath_isLow() {
            assertThat(pathBased.classify("src/test/java/com/example/service/UserServiceTest.java"))
                    .isEqualTo(FileImportance.LOW);
        }

        @Test
        @DisplayName("docs 경로 파일은 LOW이어야 한다")
        void docsPath_isLow() {
            assertThat(pathBased.classify("docs/api-spec.md"))
                    .isEqualTo(FileImportance.LOW);
        }

        @Test
        @DisplayName("설정/DTO 파일은 MEDIUM이어야 한다")
        void configPath_isMedium() {
            assertThat(pathBased.classify("src/main/java/com/example/dto/UserResponse.java"))
                    .isEqualTo(FileImportance.MEDIUM);
        }

        @Test
        @DisplayName("null 또는 빈 경로는 MEDIUM이어야 한다")
        void nullOrEmpty_isMedium() {
            assertThat(pathBased.classify(null)).isEqualTo(FileImportance.MEDIUM);
            assertThat(pathBased.classify("")).isEqualTo(FileImportance.MEDIUM);
        }

        @Test
        @DisplayName("CRITICAL > HIGH > MEDIUM > LOW 순으로 score 값이 커야 한다")
        void scoreOrdering_isCorrect() {
            assertThat(FileImportance.CRITICAL.getScore())
                    .isGreaterThan(FileImportance.HIGH.getScore())
                    .isGreaterThan(FileImportance.MEDIUM.getScore())
                    .isGreaterThan(FileImportance.LOW.getScore());
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. HistoryBasedFileImportance — 이력 통계 정규화
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HistoryBasedFileImportance")
    class HistoryBasedTests {

        @Test
        @DisplayName("빈 통계는 최솟값 0.5를 반환해야 한다")
        void emptyStats_returnsMinScore() {
            double score = historyBased.score(FileHistoryStats.empty());
            assertThat(score).isCloseTo(0.5, within(0.001));
        }

        @Test
        @DisplayName("변경 많고 기여자 많으면 1.5(최댓값)에 수렴해야 한다")
        void highActivity_convergesTo1_5() {
            // raw = 30×0.5 + 20×1.0 + 10×0.3 = 38.0 → clamp(38/20,0,1)=1.0 → 0.5+1.0=1.5
            FileHistoryStats stats = new FileHistoryStats(30, 20, 10);
            double score = historyBased.score(stats);
            assertThat(score).isCloseTo(1.5, within(0.001));
        }

        @Test
        @DisplayName("중간 활동 파일은 0.5~1.5 사이를 반환해야 한다")
        void moderateActivity_inRange() {
            // raw = 10×0.5 + 5×1.0 + 0×0.3 = 10.0 → 10/20=0.5 → 0.5+0.5=1.0
            FileHistoryStats stats = new FileHistoryStats(10, 5, 0);
            double score = historyBased.score(stats);
            assertThat(score).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("점수는 항상 0.5 이상 1.5 이하이어야 한다")
        void score_alwaysInBounds() {
            assertThat(historyBased.score(FileHistoryStats.empty())).isGreaterThanOrEqualTo(0.5);
            assertThat(historyBased.score(new FileHistoryStats(1000, 1000, 1000))).isLessThanOrEqualTo(1.5);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. FileImportanceCalculator — 결합 점수
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FileImportanceCalculator")
    class CalculatorTests {

        @Test
        @DisplayName("보안 파일(CRITICAL) + 높은 이력 → 최고 중요도이어야 한다")
        void criticalPath_highHistory_topScore() {
            double score = calculator.calculate(
                    "src/auth/JwtTokenProvider.java",
                    new FileHistoryStats(30, 20, 10));
            // pathScore=2.0×0.6 + histScore=1.5×0.4 = 1.2+0.6 = 1.8
            assertThat(score).isCloseTo(1.8, within(0.01));
        }

        @Test
        @DisplayName("테스트 파일(LOW) + 빈 이력 → 낮은 중요도이어야 한다")
        void lowPath_emptyHistory_lowScore() {
            double score = calculator.calculatePathOnly("src/test/UserServiceTest.java");
            // pathScore=0.5×0.6 + histScore=0.5×0.4 = 0.30+0.20 = 0.50
            assertThat(score).isCloseTo(0.5, within(0.01));
        }

        @Test
        @DisplayName("경로만 계산 시 history=empty 와 동일해야 한다")
        void pathOnlyEqualsEmptyHistory() {
            String path = "src/main/java/com/example/service/UserService.java";
            assertThat(calculator.calculatePathOnly(path))
                    .isEqualTo(calculator.calculate(path, FileHistoryStats.empty()));
        }

        @Test
        @DisplayName("결합 점수는 항상 0.3 이상 2.5 이하이어야 한다")
        void combinedScore_alwaysInBounds() {
            assertThat(calculator.calculate("any/auth/Security.java", new FileHistoryStats(1000, 1000, 1000)))
                    .isLessThanOrEqualTo(2.5);
            assertThat(calculator.calculate("", FileHistoryStats.empty()))
                    .isGreaterThanOrEqualTo(0.3);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. TypeScoreCalculator 통합 — fileImportance 변경 시 랭킹 변화
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TypeScoreCalculator 통합: fileImportance 반영")
    class IntegrationTests {

        @Test
        @DisplayName("보안 파일 기여에 CRITICAL 중요도 적용 시 해당 사용자 점수가 상승해야 한다")
        void applyingCriticalImportance_raisesScore() {
            // Alice PR 기여 1건에 fileImportance 기본값(1.0) 적용
            List<Contribution> base = ContributionFixture.aliceContributions();
            Map<Long, UserScore> baseScores = typeScoreCalculator.calculate(base, null);
            double baseTotal = baseScores.get(ContributionFixture.ALICE.getId()).total();

            // 동일한 기여에 CRITICAL(2.0) fileImportance 적용한 버전 생성
            List<Contribution> boosted = base.stream()
                    .map(c -> Contribution.builder()
                            .user(c.getUser())
                            .project(c.getProject())
                            .type(c.getType())
                            .githubId(c.getGithubId())
                            .count(c.getCount())
                            .ccnScore(c.getCcnScore())
                            .fileImportance(FileImportance.CRITICAL.getScore())  // 2.0
                            .rawScore(c.getCount() * c.getCcnScore() * FileImportance.CRITICAL.getScore() * c.getType().getWeight())
                            .occurredAt(c.getOccurredAt())
                            .build())
                    .toList();

            Map<Long, UserScore> boostedScores = typeScoreCalculator.calculate(boosted, null);
            double boostedTotal = boostedScores.get(ContributionFixture.ALICE.getId()).total();

            assertThat(boostedTotal).isGreaterThan(baseTotal);
        }

        @Test
        @DisplayName("LOW 파일 기여자는 CRITICAL 파일 기여자보다 점수가 낮아야 한다")
        void lowImportance_lowerThan_criticalImportance() {
            double criticalScore = FileImportance.CRITICAL.getScore();
            double lowScore      = FileImportance.LOW.getScore();

            assertThat(criticalScore).isGreaterThan(lowScore);

            // 같은 PR 1건이지만 파일 중요도만 다른 경우
            double prWeight = com.equicode.gitequity.domain.ContributionType.PR.getWeight();
            double withCritical = 1 * 1.0 * criticalScore * prWeight;
            double withLow      = 1 * 1.0 * lowScore      * prWeight;

            assertThat(withCritical).isGreaterThan(withLow);
        }
    }
}
