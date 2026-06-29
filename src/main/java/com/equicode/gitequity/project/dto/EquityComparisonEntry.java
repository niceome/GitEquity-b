package com.equicode.gitequity.project.dto;

public record EquityComparisonEntry(
        Long userId,
        String username,
        double targetEquity,
        Double finalEquity,  // null if project not yet COMPLETED
        Double delta         // finalEquity - targetEquity, null if not yet determined
) {}
