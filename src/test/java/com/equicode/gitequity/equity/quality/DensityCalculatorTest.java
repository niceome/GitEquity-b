package com.equicode.gitequity.equity.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DensityCalculatorTest {

    private final DensityCalculator calculator = new DensityCalculator();

    @Test
    @DisplayName("Java diff: 주석/빈줄/import/괄호 라인을 제외한 실질 라인만 net으로 집계한다")
    void javaDiff_countsOnlySubstantiveLines() {
        String patch = """
                @@ -10,6 +10,12 @@ public class Foo {
                 public class Foo {
                -    public String greet() {
                -        return "hi";
                -    }
                +    // returns a friendly greeting
                +    public String greet() {
                +        return "hello, world";
                +    }
                +
                +    import java.util.List;
                 }""";

        DensityResult result = calculator.analyze(patch);

        // gross: -greet(){, -return, -}, +comment, +greet(){, +return, +}, +blank, +import = 9
        // net  : -greet(){, -return, +greet(){, +return = 4
        assertThat(result.grossLines()).isEqualTo(9);
        assertThat(result.netLines()).isEqualTo(4);
        assertThat(result.density()).isCloseTo(4.0 / 9.0, within(0.0001));
    }

    @Test
    @DisplayName("Python diff: from-import와 빈 줄을 제외한 실질 라인만 net으로 집계한다")
    void pythonDiff_excludesImportAndBlankLines() {
        String patch = """
                @@ -1,5 +1,9 @@
                +from typing import List
                +
                +
                 def greet(name):
                -    return "hi " + name
                +    # return a friendly greeting
                +    return "hello, " + name
                +
                +
                 def farewell(name):
                     return "bye " + name""";

        DensityResult result = calculator.analyze(patch);

        // gross: +import, +blank, +blank, -return, +comment, +return, +blank, +blank = 8
        // net  : -return, +return = 2
        assertThat(result.grossLines()).isEqualTo(8);
        assertThat(result.netLines()).isEqualTo(2);
        assertThat(result.density()).isCloseTo(0.25, within(0.0001));
    }

    @Test
    @DisplayName("빈 줄만 추가된 diff는 density가 0이어야 한다")
    void blankLinesOnlyDiff_densityIsZero() {
        String patch = """
                @@ -10,0 +11,2 @@
                +
                +""";

        DensityResult result = calculator.analyze(patch);

        assertThat(result.grossLines()).isEqualTo(2);
        assertThat(result.netLines()).isEqualTo(0);
        assertThat(result.density()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("주석만 추가된 diff는 density가 0이어야 한다 (블록 주석 포함)")
    void commentOnlyDiff_densityIsZero() {
        String patch = """
                @@ -5,3 +5,6 @@ public class Foo {
                 public class Foo {
                +    // TODO: handle edge case
                +    /**
                +     * Javadoc line
                +     */
                     public void bar() {}
                 }""";

        DensityResult result = calculator.analyze(patch);

        assertThat(result.grossLines()).isEqualTo(4);
        assertThat(result.netLines()).isEqualTo(0);
        assertThat(result.density()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("patch가 null이면 zero 결과를 반환한다")
    void nullPatch_returnsZero() {
        DensityResult result = calculator.analyze(null);

        assertThat(result.grossLines()).isEqualTo(0);
        assertThat(result.netLines()).isEqualTo(0);
        assertThat(result.density()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("patch가 빈 문자열이면 zero 결과를 반환한다")
    void blankPatch_returnsZero() {
        DensityResult result = calculator.analyze("   ");

        assertThat(result.grossLines()).isEqualTo(0);
        assertThat(result.netLines()).isEqualTo(0);
    }

    @Test
    @DisplayName("파일 헤더(+++/---) 라인은 변경 라인 집계에서 제외한다")
    void fileHeaderLines_areExcluded() {
        String patch = """
                --- a/src/Foo.java
                +++ b/src/Foo.java
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    public void bar() {}
                 }""";

        DensityResult result = calculator.analyze(patch);

        assertThat(result.grossLines()).isEqualTo(1);
        assertThat(result.netLines()).isEqualTo(1);
    }
}
