package com.equicode.gitequity.contract.dto;

import java.time.LocalDateTime;

public record MemberSignatureStatus(
        Long userId,
        String username,
        double percentage,
        boolean signed,
        LocalDateTime signedAt
) {}
