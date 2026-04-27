package com.equicode.gitequity.equity;

import com.equicode.gitequity.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/equity")
@RequiredArgsConstructor
public class EquityController {

    private final EquityCalculatorService equityCalculatorService;
    private final EquitySnapshotService   equitySnapshotService;

    // ── 실시간 계산 (저장 없음) ────────────────────────────────────────────────

    @GetMapping
    public ApiResponse<EquityResult> calculate(@PathVariable Long projectId) {
        EquityResult result = equityCalculatorService.calculate(projectId);
        return ApiResponse.ok(
                "%d contributors, total=%.2f".formatted(result.equities().size(), result.totalRawScore()),
                result);
    }

    // ── 스냅샷 생성 (현재 지분 freeze) ──────────────────────────────────────────

    @PostMapping("/snapshot")
    public ApiResponse<SnapshotSummary> createSnapshot(@PathVariable Long projectId) {
        SnapshotSummary summary = equitySnapshotService.createSnapshot(projectId);
        return ApiResponse.ok(
                "snapshot saved: %d participants at %s"
                        .formatted(summary.participantCount(), summary.snapshotAt()),
                summary);
    }

    // ── 스냅샷 이력 — 생성 시각 목록 ─────────────────────────────────────────

    @GetMapping("/snapshots")
    public ApiResponse<List<LocalDateTime>> listSnapshots(@PathVariable Long projectId) {
        List<LocalDateTime> timestamps = equitySnapshotService.getSnapshotTimestamps(projectId);
        return ApiResponse.ok("%d snapshots found".formatted(timestamps.size()), timestamps);
    }

    // ── 스냅샷 이력 — 최신 스냅샷 상세 ──────────────────────────────────────────

    @GetMapping("/snapshots/latest")
    public ApiResponse<SnapshotSummary> latestSnapshot(@PathVariable Long projectId) {
        SnapshotSummary summary = equitySnapshotService.getLatestSnapshot(projectId);
        return ApiResponse.ok("latest snapshot", summary);
    }

    // ── 스냅샷 이력 — 특정 시각 스냅샷 상세 ─────────────────────────────────────

    @GetMapping("/snapshots/{at}")
    public ApiResponse<SnapshotSummary> snapshotAt(
            @PathVariable Long projectId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at) {
        SnapshotSummary summary = equitySnapshotService.getSnapshotAt(projectId, at);
        return ApiResponse.ok("snapshot at " + at, summary);
    }
}
