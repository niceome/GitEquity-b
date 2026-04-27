package com.equicode.gitequity.equity;

import java.time.LocalDateTime;
import java.util.List;

/** 스냅샷 생성 / 이력 조회 공통 응답 */
public record SnapshotSummary(
        Long projectId,
        LocalDateTime snapshotAt,
        int participantCount,
        double totalRawScore,
        List<UserEquitySnapshot> users
) {
    public static SnapshotSummary empty(Long projectId) {
        return new SnapshotSummary(projectId, LocalDateTime.now(), 0, 0.0, List.of());
    }
}
