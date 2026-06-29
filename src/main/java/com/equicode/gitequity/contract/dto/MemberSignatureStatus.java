package com.equicode.gitequity.contract.dto;

import java.time.LocalDateTime;

public record MemberSignatureStatus(
        Long userId,
        String username,
        double percentage,     // INITIAL=0, FINAL=실제기여도지분
        boolean signed,
        LocalDateTime signedAt
) {}
