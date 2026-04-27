package com.equicode.gitequity.equity;

import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.common.exception.ErrorCode;
import com.equicode.gitequity.domain.*;
import com.equicode.gitequity.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EquitySnapshotService {

    private final EquityCalculatorService  equityCalculatorService;
    private final ProjectRepository        projectRepository;
    private final UserRepository           userRepository;
    private final ContributionRepository   contributionRepository;
    private final EquitySnapshotRepository snapshotRepository;

    // ── 스냅샷 생성 ───────────────────────────────────────────────────────────

    @Transactional
    public SnapshotSummary createSnapshot(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        EquityResult equityResult = equityCalculatorService.calculate(projectId);

        if (equityResult.equities().isEmpty()) {
            log.warn("[Snapshot] project={} has no contributions — snapshot skipped", projectId);
            return SnapshotSummary.empty(projectId);
        }

        // 기여 타입별 건수 집계: userId → (type → count)
        Map<Long, Map<ContributionType, Long>> countsByUser =
                contributionRepository.findByProjectId(projectId).stream()
                        .collect(Collectors.groupingBy(
                                c -> c.getUser().getId(),
                                Collectors.groupingBy(Contribution::getType, Collectors.counting())));

        LocalDateTime snapshotAt = equityResult.calculatedAt();
        List<EquitySnapshot> toSave = new ArrayList<>();

        for (UserEquity equity : equityResult.equities()) {
            User user = userRepository.findById(equity.userId()).orElse(null);
            if (user == null) continue;

            Map<ContributionType, Long> counts = countsByUser.getOrDefault(equity.userId(), Map.of());

            toSave.add(EquitySnapshot.builder()
                    .project(project)
                    .user(user)
                    .percentage(equity.percentage())
                    .totalCommits(count(counts, ContributionType.COMMIT))
                    .totalPrs(count(counts, ContributionType.PR))
                    .totalReviews(count(counts, ContributionType.REVIEW))
                    .totalIssues(count(counts, ContributionType.ISSUE))
                    .rawScore(equity.rawScore())
                    .snapshotAt(snapshotAt)
                    .build());
        }

        List<EquitySnapshot> saved = snapshotRepository.saveAll(toSave);

        log.info("[Snapshot] project={} saved {} snapshots at {}",
                projectId, saved.size(), snapshotAt);

        return toSnapshotSummary(projectId, snapshotAt, equityResult.totalRawScore(), saved);
    }

    // ── 이력 조회 ─────────────────────────────────────────────────────────────

    /**
     * 스냅샷 생성 시각 목록 반환 (최신순)
     * 각 시각은 하나의 스냅샷 그룹(모든 멤버의 동시 기록)을 의미한다.
     */
    @Transactional(readOnly = true)
    public List<LocalDateTime> getSnapshotTimestamps(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new CustomException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return snapshotRepository.findDistinctSnapshotAtByProjectId(projectId);
    }

    /**
     * 특정 시점의 스냅샷 그룹 상세 조회
     */
    @Transactional(readOnly = true)
    public SnapshotSummary getSnapshotAt(Long projectId, LocalDateTime snapshotAt) {
        List<EquitySnapshot> snapshots =
                snapshotRepository.findByProjectIdAndSnapshotAt(projectId, snapshotAt);
        if (snapshots.isEmpty()) {
            throw new CustomException(ErrorCode.PROJECT_NOT_FOUND); // 적절한 에러코드 없어 임시 사용
        }
        double totalRaw = snapshots.stream().mapToDouble(EquitySnapshot::getRawScore).sum();
        return toSnapshotSummary(projectId, snapshotAt, totalRaw, snapshots);
    }

    /**
     * 가장 최근 스냅샷 그룹 조회
     */
    @Transactional(readOnly = true)
    public SnapshotSummary getLatestSnapshot(Long projectId) {
        return snapshotRepository.findTopByProjectIdOrderBySnapshotAtDesc(projectId)
                .map(top -> getSnapshotAt(projectId, top.getSnapshotAt()))
                .orElse(SnapshotSummary.empty(projectId));
    }

    // ── 변환 헬퍼 ─────────────────────────────────────────────────────────────

    private SnapshotSummary toSnapshotSummary(Long projectId, LocalDateTime snapshotAt,
                                               double totalRaw, List<EquitySnapshot> snapshots) {
        List<UserEquitySnapshot> users = snapshots.stream()
                .sorted(Comparator.comparingDouble(EquitySnapshot::getPercentage).reversed())
                .map(s -> new UserEquitySnapshot(
                        s.getId(),
                        s.getUser().getId(),
                        s.getUser().getUsername(),
                        s.getPercentage(),
                        s.getRawScore(),
                        s.getTotalCommits(),
                        s.getTotalPrs(),
                        s.getTotalReviews(),
                        s.getTotalIssues()))
                .toList();

        return new SnapshotSummary(projectId, snapshotAt, users.size(), totalRaw, users);
    }

    private int count(Map<ContributionType, Long> map, ContributionType type) {
        return map.getOrDefault(type, 0L).intValue();
    }
}
