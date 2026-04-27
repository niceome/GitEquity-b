package com.equicode.gitequity.equity;

/** 저장된 스냅샷 단건 응답 — DB에 persist된 후 ID 포함 */
public record UserEquitySnapshot(
        Long snapshotId,
        Long userId,
        String username,
        double percentage,
        double rawScore,
        int commits,
        int prs,
        int reviews,
        int issues
) {}
