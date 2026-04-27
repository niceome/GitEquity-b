package com.equicode.gitequity.project.dto;

import com.equicode.gitequity.domain.Project;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ProjectDetailResponse(
        Long id,
        String name,
        String repoOwner,
        String repoName,
        String repoUrl,
        Map<String, Double> weightConfig,
        int memberCount,
        LocalDateTime createdAt,
        List<ProjectMemberResponse> members
) {
    public static ProjectDetailResponse from(Project p, List<ProjectMemberResponse> members) {
        return new ProjectDetailResponse(
                p.getId(),
                p.getName(),
                p.getRepoOwner(),
                p.getRepoName(),
                p.getRepoUrl(),
                p.getWeightConfig(),
                members.size(),
                p.getCreatedAt(),
                members);
    }
}
