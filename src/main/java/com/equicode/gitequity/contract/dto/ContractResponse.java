package com.equicode.gitequity.contract.dto;

import com.equicode.gitequity.domain.Contract;
import com.equicode.gitequity.domain.ContractStatus;

import java.time.LocalDateTime;

public record ContractResponse(
        Long id,
        Long projectId,
        ContractStatus status,
        Long snapshotGroupId,
        String pdfUrl,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public static ContractResponse from(Contract c) {
        return new ContractResponse(
                c.getId(), c.getProject().getId(), c.getStatus(),
                c.getSnapshotGroupId(), c.getPdfUrl(),
                c.getCreatedAt(), c.getCompletedAt());
    }
}
