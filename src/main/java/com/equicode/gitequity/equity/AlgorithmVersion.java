package com.equicode.gitequity.equity;

/**
 * 지분 산출 알고리즘 버전 (S16)
 *
 * EquityResult에 담겨 계약 체결 시점에 signatures 테이블로 기록된다.
 */
public final class AlgorithmVersion {

    // PR/리뷰/코멘트 3기준 가중치(2:1:1), 이슈 제외 (EquityCalculatorService)
    public static final String CURRENT = "3.0.0";

    // commit/PR/review/issue 개수 × 가중치 기반 기존 공식 (LegacyEquityCalculatorService)
    public static final String LEGACY = "1.0.0";

    private AlgorithmVersion() {}
}
