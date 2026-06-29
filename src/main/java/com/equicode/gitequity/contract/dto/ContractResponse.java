package com.equicode.gitequity.contract.dto;

import com.equicode.gitequity.domain.Contract;
import com.equicode.gitequity.domain.ContractStatus;
import com.equicode.gitequity.domain.ContractType;

import java.time.LocalDateTime;

public record ContractResponse(
        Long id,
        Long projectId,
        ContractType contractType,
        ContractStatus status,
        Long snapshotGroupId,
        String pdfUrl,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    public static ContractResponse from(Contract c) {
        return new ContractResponse(
                c.getId(), c.getProject().getId(), c.getContractType(), c.getStatus(),
                c.getSnapshotGroupId(), c.getPdfUrl(),
                c.getCreatedAt(), c.getCompletedAt());
    }
}
