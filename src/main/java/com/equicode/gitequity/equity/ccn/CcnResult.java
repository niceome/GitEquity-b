package com.equicode.gitequity.equity.ccn;

/**
 * 파일 1개에 대한 Lizard CCN 분석 결과
 *
 * ccnScore 정규화 공식:
 *   ccnScore = clamp(avgCcn / CCN_BASELINE, MIN_SCORE, MAX_SCORE)
 *   - CCN_BASELINE = 5.0 (CCN=5 이하 → 1.0, 즉 기본값)
 *   - MIN_SCORE    = 0.5 (단순 코드는 기여 가치가 약간 낮음)
 *   - MAX_SCORE    = 3.0 (매우 복잡한 코드의 상한)
 *
 * 예시:
 *   avgCcn=1  → ccnScore=0.5  (min floor)
 *   avgCcn=5  → ccnScore=1.0  (baseline)
 *   avgCcn=10 → ccnScore=2.0
 *   avgCcn=15 → ccnScore=3.0  (max cap)
 */
public record CcnResult(
        String filename,
        double avgCcn,
        int functionCount
) {
    private static final double CCN_BASELINE  = 5.0;
    private static final double MIN_SCORE     = 0.5;
    private static final double MAX_SCORE     = 3.0;

    public double ccnScore() {
        double raw = avgCcn / CCN_BASELINE;
        return Math.min(MAX_SCORE, Math.max(MIN_SCORE, raw));
    }

    public static CcnResult unknown(String filename) {
        return new CcnResult(filename, 5.0, 0); // CCN 측정 불가 시 기본값(1.0) 반환
    }
}
