package com.equicode.gitequity.contract.dto;

import java.time.LocalDateTime;
import java.util.List;

/** PDF 생성에 필요한 계약 데이터 */
public record ContractPdfData(
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
            double percentage,
            double rawScore,
            int commits,
            int prs,
            int reviews,
            int issues,
            LocalDateTime signedAt,
            String ipAddress
    ) {}
}
