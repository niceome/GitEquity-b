package com.equicode.gitequity.equity;

import com.equicode.gitequity.claim.ClaimAdjustmentService;
import com.equicode.gitequity.common.response.ApiResponse;
import com.equicode.gitequity.equity.comparison.ComparisonReport;
import com.equicode.gitequity.equity.comparison.ComparisonResponse;
import com.equicode.gitequity.equity.comparison.EquityComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/equity")
@RequiredArgsConstructor
public class EquityController {

    private final EquityCalculatorService   equityCalculatorService;
    private final EquitySnapshotService     equitySnapshotService;
    private final EquityComparisonService   equityComparisonService;
    private final ClaimAdjustmentService    claimAdjustmentService;

    // ── 실시간 계산 (저장 없음) ────────────────────────────────────────────────

    @GetMapping
    public ApiResponse<EquityResult> calculate(@PathVariable Long projectId) {
        EquityResult result = equityCalculatorService.calculate(projectId);
        return ApiResponse.ok(
                "%d contributors, total=%.2f".formatted(result.equities().size(), result.totalRawScore()),
                result);
    }

    // ── 확정된 비코드 기여(claim) 보정이 반영된 지분 ─────────────────────────────

    @GetMapping("/adjusted")
    public ApiResponse<EquityResult> calculateAdjusted(@PathVariable Long projectId) {
        EquityResult result = claimAdjustmentService.applyAdjustment(projectId);
        return ApiResponse.ok(
                "%d contributors, total=%.2f (claim-adjusted)".formatted(result.equities().size(), result.totalRawScore()),
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

    // ── S10: 개선 전(legacy) vs 개선 후(신규) 지분 비교 리포트 ───────────────────
    // body: 동료 평가 점수 (userId → score). 없으면 Pearson 상관계수/괴리 알림은 생략된다.

    @PostMapping("/comparison")
    public ApiResponse<ComparisonResponse> compare(
            @PathVariable Long projectId,
            @RequestBody(required = false) Map<Long, Double> peerScores) {
        ComparisonReport report = equityComparisonService.compare(projectId, peerScores);
        String consoleTable = equityComparisonService.toConsoleTable(report);
        return ApiResponse.ok(
                "%d users compared, %d gap alerts".formatted(report.users().size(), report.gapAlerts().size()),
                new ComparisonResponse(report, consoleTable));
    }
}
