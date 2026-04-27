package com.equicode.gitequity.equity.ccn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CcnAnalyzer 통합 테스트
 * lizard가 설치되지 않은 환경에서는 전체 테스트를 skip한다.
 *
 * 설치: pip install lizard
 */
class CcnAnalyzerTest {

    private CcnAnalyzer analyzer;

    // ── CCN=1: 단순 메서드 (분기 없음) ────────────────────────────────────────
    private static final String SIMPLE_JAVA = """
            public class Simple {
                public int add(int a, int b) {
                    return a + b;
                }
            }
            """;

    // ── CCN=4: if-else 체인 (분기 3개 + 기본 1) ───────────────────────────────
    private static final String MODERATE_JAVA = """
            public class Moderate {
                public String grade(int score) {
                    if (score >= 90) {
                        return "A";
                    } else if (score >= 80) {
                        return "B";
                    } else if (score >= 70) {
                        return "C";
                    } else {
                        return "F";
                    }
                }
            }
            """;

    // ── CCN=7: 중첩 루프 + 조건 ───────────────────────────────────────────────
    private static final String COMPLEX_JAVA = """
            public class Complex {
                public int compute(int[][] matrix, int target) {
                    int count = 0;
                    for (int i = 0; i < matrix.length; i++) {
                        for (int j = 0; j < matrix[i].length; j++) {
                            if (matrix[i][j] > target) {
                                count++;
                            } else if (matrix[i][j] == target) {
                                return -1;
                            } else if (matrix[i][j] < 0) {
                                continue;
                            }
                        }
                        if (count > 100) {
                            break;
                        }
                    }
                    return count;
                }
            }
            """;

    // ── Python 샘플 (CCN=3) ───────────────────────────────────────────────────
    private static final String PYTHON_CODE = """
            def classify(x):
                if x > 0:
                    if x > 100:
                        return "large"
                    return "positive"
                elif x < 0:
                    return "negative"
                return "zero"
            """;

    @BeforeEach
    void setUp() {
        analyzer = new CcnAnalyzer();
        assumeTrue(analyzer.isLizardAvailable(),
                "lizard not installed — skipping CCN tests. Install: pip install lizard");
    }

    // ── 1. CCN 측정값 범위 검증 ────────────────────────────────────────────────

    @Test
    @DisplayName("단순 메서드(분기 없음)는 CCN=1, ccnScore=MIN(0.5)이어야 한다")
    void simpleCode_shouldHaveCcn1() {
        CcnResult result = analyzer.analyze(SIMPLE_JAVA, "Simple.java");

        assertThat(result.functionCount()).isGreaterThan(0);
        assertThat(result.avgCcn()).isCloseTo(1.0, within(0.5));
        assertThat(result.ccnScore()).isCloseTo(0.5, within(0.1));  // CCN=1 → 1/5=0.2, clamp to 0.5
    }

    @Test
    @DisplayName("if-else 4분기 메서드는 CCN=4, ccnScore≈0.8이어야 한다")
    void moderateCode_shouldHaveCcn4() {
        CcnResult result = analyzer.analyze(MODERATE_JAVA, "Moderate.java");

        assertThat(result.avgCcn()).isCloseTo(4.0, within(1.0));
        assertThat(result.ccnScore()).isGreaterThan(0.5).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("중첩 루프 복잡 메서드는 CCN≥6이어야 한다")
    void complexCode_shouldHaveHighCcn() {
        CcnResult result = analyzer.analyze(COMPLEX_JAVA, "Complex.java");

        assertThat(result.avgCcn()).isGreaterThanOrEqualTo(6.0);
        assertThat(result.ccnScore()).isGreaterThan(1.0);
    }

    // ── 2. ccnScore 정규화 범위 검증 ──────────────────────────────────────────

    @Test
    @DisplayName("ccnScore는 항상 MIN(0.5) 이상 MAX(3.0) 이하이어야 한다")
    void ccnScore_shouldBeWithinBounds() {
        for (String code : new String[]{SIMPLE_JAVA, MODERATE_JAVA, COMPLEX_JAVA}) {
            CcnResult result = analyzer.analyze(code, "Test.java");
            assertThat(result.ccnScore())
                    .isGreaterThanOrEqualTo(0.5)
                    .isLessThanOrEqualTo(3.0);
        }
    }

    // ── 3. 복잡한 코드 > 단순한 코드 검증 ────────────────────────────────────

    @Test
    @DisplayName("복잡한 코드의 ccnScore가 단순한 코드보다 높아야 한다")
    void complexCode_shouldHaveHigherScore_thanSimpleCode() {
        CcnResult simple  = analyzer.analyze(SIMPLE_JAVA, "Simple.java");
        CcnResult complex = analyzer.analyze(COMPLEX_JAVA, "Complex.java");

        assertThat(complex.ccnScore()).isGreaterThan(simple.ccnScore());
    }

    // ── 4. Python 지원 검증 ────────────────────────────────────────────────────

    @Test
    @DisplayName("Python 코드도 분석 가능해야 한다")
    void pythonCode_shouldBeAnalyzable() {
        CcnResult result = analyzer.analyze(PYTHON_CODE, "classify.py");

        assertThat(result.functionCount()).isGreaterThan(0);
        assertThat(result.avgCcn()).isGreaterThanOrEqualTo(1.0);
    }

    // ── 5. 빈 파일 처리 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("빈 파일은 functionCount=0, ccnScore=1.0(unknown default)을 반환해야 한다")
    void emptyFile_shouldReturnDefault() {
        CcnResult result = analyzer.analyze("", "Empty.java");

        assertThat(result.functionCount()).isEqualTo(0);
        assertThat(result.ccnScore()).isCloseTo(1.0, within(0.1));
    }

    // ── 6. unknown 팩토리 메서드 검증 ─────────────────────────────────────────

    @Test
    @DisplayName("CcnResult.unknown()은 ccnScore=1.0을 반환해야 한다")
    void unknownResult_shouldHaveDefaultScore() {
        CcnResult result = CcnResult.unknown("any.java");

        assertThat(result.ccnScore()).isCloseTo(1.0, within(0.001));
    }
}
