package com.equicode.gitequity.user.dto;

import com.equicode.gitequity.domain.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String avatarUrl,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getCreatedAt()
        );
    }
}
