package com.equicode.gitequity.equity.quality;

/**
 * 한 PR(또는 파일)의 source code density 측정 결과
 *
 * - grossLines: unified diff에서 +/- 로 시작하는 변경 라인 총 개수 (헤더 제외)
 * - netLines  : grossLines 중 "실질적인" 변경으로 판단된 라인 개수
 * - density   : netLines / grossLines (grossLines == 0이면 0)
 */
public record DensityResult(int netLines, int grossLines) {

    public static DensityResult zero() {
        return new DensityResult(0, 0);
    }

    public double density() {
        return grossLines == 0 ? 0.0 : (double) netLines / grossLines;
    }

    public DensityResult plus(DensityResult other) {
        return new DensityResult(netLines + other.netLines(), grossLines + other.grossLines());
    }
}
