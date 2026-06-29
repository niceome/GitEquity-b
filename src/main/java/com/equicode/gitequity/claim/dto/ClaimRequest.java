package com.equicode.gitequity.claim.dto;

import com.equicode.gitequity.domain.NonCodeContributionType;

import java.time.LocalDate;

public record ClaimRequest(
        Long beneficiaryId,
        NonCodeContributionType type,
        String description,
        LocalDate occurredAt
) {}
