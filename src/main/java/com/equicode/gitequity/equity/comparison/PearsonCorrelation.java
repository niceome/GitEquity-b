package com.equicode.gitequity.equity.comparison;

/**
 * Pearson 상관계수 계산 (S10 개선 전후 비교 리포트용)
 *
 * 표본 수가 2 미만이거나 어느 한쪽의 분산이 0이면(모든 값이 동일) 계산이
 * 불가능하므로 null을 반환한다.
 */
public final class PearsonCorrelation {

    private PearsonCorrelation() {}

    public static Double compute(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) return null;

        double meanX = mean(x);
        double meanY = mean(y);

        double numerator = 0.0;
        double sumSqX = 0.0;
        double sumSqY = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            numerator += dx * dy;
            sumSqX += dx * dx;
            sumSqY += dy * dy;
        }

        double denominator = Math.sqrt(sumSqX * sumSqY);
        if (denominator == 0.0) return null;

        return numerator / denominator;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.length;
    }
}
