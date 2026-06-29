package com.equicode.gitequity.claim.dto;

import com.equicode.gitequity.domain.ClaimStatus;
import com.equicode.gitequity.domain.NonCodeContribution;
import com.equicode.gitequity.domain.NonCodeContributionType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClaimResponse(
        Long id,
        Long projectId,
        Long claimerId,
        String claimerUsername,
        Long beneficiaryId,
        String beneficiaryUsername,
        NonCodeContributionType type,
        String description,
        LocalDate occurredAt,
        ClaimStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt
) {
    public static ClaimResponse from(NonCodeContribution c) {
        return new ClaimResponse(
                c.getId(),
                c.getProject().getId(),
                c.getClaimer().getId(),
                c.getClaimer().getUsername(),
                c.getBeneficiary().getId(),
                c.getBeneficiary().getUsername(),
                c.getType(),
                c.getDescription(),
                c.getOccurredAt(),
                c.getStatus(),
                c.getConfirmedAt(),
                c.getCreatedAt());
    }
}
