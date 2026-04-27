package com.equicode.gitequity.contract.dto;

import com.equicode.gitequity.domain.ContractStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ContractDetailResponse(
        Long id,
        Long projectId,
        String projectName,
        ContractStatus status,
        String pdfUrl,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        int totalMembers,
        long signedCount,
        List<MemberSignatureStatus> members
) {}
