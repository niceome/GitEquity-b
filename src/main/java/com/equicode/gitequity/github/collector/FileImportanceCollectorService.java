package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.ContributionType;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.equity.importance.FileHistoryStats;
import com.equicode.gitequity.equity.importance.FileImportanceCalculator;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.github.dto.PullRequestFileDto;
import com.equicode.gitequity.repository.ContributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * COMMIT·PR 기여의 fileImportance를 계산한다.
 *
 * 처리 순서:
 *   1. PR·COMMIT 기여의 변경 파일 목록을 각각 GitHub API로 조회 (단일 패스)
 *   2. 조회 결과로 파일별 (변경 횟수, 기여자 집합) 집계 → FileHistoryStats 맵 생성
 *   3. 기여별로 변경 파일들의 평균 fileImportance 계산 후 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileImportanceCollectorService {

    private final GithubApiClient githubApiClient;
    private final ContributionRepository contributionRepository;
    private final FileImportanceCalculator fileImportanceCalculator;

    @Transactional
    public int collect(Project project, String token) {
        List<Contribution> prContribs =
                contributionRepository.findByProjectIdAndType(project.getId(), ContributionType.PR);
        List<Contribution> commitContribs =
                contributionRepository.findByProjectIdAndType(project.getId(), ContributionType.COMMIT);

        // 기여별 변경 파일 목록을 한 번만 조회 (삽입 순서 보장)
        Map<Contribution, List<PullRequestFileDto>> contribFiles = new LinkedHashMap<>();
        for (Contribution c : prContribs) {
            try {
                contribFiles.put(c, fetchAllPrFiles(project, token, Integer.parseInt(c.getGithubId())));
            } catch (Exception e) {
                log.warn("[FileImportance] fetch failed PR={} project={}: {}",
                        c.getGithubId(), project.getRepoName(), e.getMessage());
            }
        }
        for (Contribution c : commitContribs) {
            try {
                contribFiles.put(c, githubApiClient.fetchCommitFiles(
                        project.getRepoOwner(), project.getRepoName(), c.getGithubId(), token));
            } catch (Exception e) {
                log.warn("[FileImportance] fetch failed COMMIT={} project={}: {}",
                        c.getGithubId(), project.getRepoName(), e.getMessage());
            }
        }

        // 파일별 변경 횟수·기여자 집합 집계
        Map<String, FileHistoryStats> fileStatsMap = buildFileStatsMap(contribFiles);

        // 기여별 fileImportance 반영
        int updated = 0;
        for (Map.Entry<Contribution, List<PullRequestFileDto>> entry : contribFiles.entrySet()) {
            Contribution c = entry.getKey();
            double importance = calcImportanceForFiles(entry.getValue(), fileStatsMap);
            c.applyFileImportance(importance, c.getType().getWeight());
            updated++;
        }

        log.info("[FileImportance] project={} updated={} files tracked={}",
                project.getRepoName(), updated, fileStatsMap.size());
        return updated;
    }

    // ── 파일별 집계 맵 생성 ───────────────────────────────────────────────────

    private Map<String, FileHistoryStats> buildFileStatsMap(
            Map<Contribution, List<PullRequestFileDto>> contribFiles) {

        Map<String, Integer> changeCounts = new HashMap<>();
        Map<String, Set<Long>> contributorSets = new HashMap<>();

        for (Map.Entry<Contribution, List<PullRequestFileDto>> entry : contribFiles.entrySet()) {
            Long userId = entry.getKey().getUser().getId();
            for (PullRequestFileDto file : entry.getValue()) {
                if ("removed".equals(file.status())) continue;
                changeCounts.merge(file.filename(), 1, Integer::sum);
                contributorSets.computeIfAbsent(file.filename(), k -> new HashSet<>()).add(userId);
            }
        }

        Map<String, FileHistoryStats> result = new HashMap<>();
        changeCounts.forEach((filename, count) -> {
            int contributorCount = contributorSets.getOrDefault(filename, Set.of()).size();
            result.put(filename, new FileHistoryStats(count, contributorCount, 0));
        });
        return result;
    }

    // ── 변경 파일 목록의 평균 fileImportance 반환 ─────────────────────────────

    private double calcImportanceForFiles(List<PullRequestFileDto> files,
                                          Map<String, FileHistoryStats> fileStatsMap) {
        List<Double> scores = new ArrayList<>();
        for (PullRequestFileDto file : files) {
            if ("removed".equals(file.status())) continue;
            FileHistoryStats stats = fileStatsMap.getOrDefault(file.filename(), FileHistoryStats.empty());
            scores.add(fileImportanceCalculator.calculate(file.filename(), stats));
        }
        OptionalDouble avg = scores.stream().mapToDouble(Double::doubleValue).average();
        return avg.orElse(1.0);
    }

    private List<PullRequestFileDto> fetchAllPrFiles(Project project, String token, int pullNumber) {
        List<PullRequestFileDto> all = new ArrayList<>();
        int page = 1;
        while (true) {
            PagedResponse<PullRequestFileDto> response = githubApiClient.fetchPullRequestFiles(
                    project.getRepoOwner(), project.getRepoName(), pullNumber, page, token);
            all.addAll(response.items());
            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }
        return all;
    }
}
