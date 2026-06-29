package com.equicode.gitequity.equity.comparison;

/**
 * S10 비교 리포트 API 응답 — JSON(report)과 콘솔 표(consoleTable)를 함께 제공한다.
 */
public record ComparisonResponse(
        ComparisonReport report,
        String consoleTable
) {}
