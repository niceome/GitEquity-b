package com.equicode.gitequity.contract.dto;

import com.equicode.gitequity.domain.ContractType;

import java.time.LocalDateTime;
import java.util.List;

public record ContractPdfData(
        ContractType contractType,
        Long contractId,
        String projectName,
        String repoOwner,
        String repoName,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<MemberPdfRow> members
) {
    public record MemberPdfRow(
            String username,
            Double equity,         // null for INITIAL, actual % for FINAL
            double rawScore,
            int commits,
            int prs,
            int reviews,
            int issues,
            LocalDateTime signedAt,
            String ipAddress
    ) {}
}
