package com.equicode.gitequity.project.dto;

import com.equicode.gitequity.domain.MemberRole;
import com.equicode.gitequity.domain.ProjectMember;

import java.time.LocalDateTime;

public record ProjectMemberResponse(
        Long userId,
        String username,
        String avatarUrl,
        MemberRole role,
        LocalDateTime joinedAt
) {
    public static ProjectMemberResponse from(ProjectMember m) {
        return new ProjectMemberResponse(
                m.getUser().getId(),
                m.getUser().getUsername(),
                m.getUser().getAvatarUrl(),
                m.getRole(),
                m.getJoinedAt());
    }
}
