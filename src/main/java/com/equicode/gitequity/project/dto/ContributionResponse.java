package com.equicode.gitequity.project.dto;

import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.ContributionType;

import java.time.LocalDateTime;

public record ContributionResponse(
        Long id,
        Long userId,
        String username,
        ContributionType type,
        String githubId,
        int count,
        double rawScore,
        LocalDateTime occurredAt
) {
    public static ContributionResponse from(Contribution c) {
        return new ContributionResponse(
                c.getId(),
                c.getUser().getId(),
                c.getUser().getUsername(),
                c.getType(),
                c.getGithubId(),
                c.getCount(),
                c.getRawScore(),
                c.getOccurredAt());
    }
}
