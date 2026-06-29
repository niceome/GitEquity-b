package com.equicode.gitequity.project.dto;

import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.ProjectPhase;

import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String name,
        String repoOwner,
        String repoName,
        String repoUrl,
        int memberCount,
        LocalDateTime createdAt,
        ProjectPhase phase
) {
    public static ProjectResponse from(Project p, int memberCount) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getRepoOwner(),
                p.getRepoName(),
                p.getRepoUrl(),
                memberCount,
                p.getCreatedAt(),
                p.getPhase());
    }
}
