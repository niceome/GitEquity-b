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

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * PR별 변경 파일의 경로 기반 중요도를 계산하여 contribution.fileImportance를 업데이트한다.
 * 이력 기반 통계는 현재 DB의 기여 데이터로 보완한다.
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
        List<Contribution> prContributions =
                contributionRepository.findByProjectIdAndType(project.getId(), ContributionType.PR);

        int updated = 0;
        for (Contribution contribution : prContributions) {
            try {
                double importance = calcImportanceForPr(
                        project, token, Integer.parseInt(contribution.getGithubId()));
                contribution.applyFileImportance(importance, ContributionType.PR.getWeight());
                updated++;
            } catch (Exception e) {
                log.warn("[FileImportance] failed PR={} project={}: {}",
                        contribution.getGithubId(), project.getRepoName(), e.getMessage());
            }
        }

        log.info("[FileImportance] project={} updated={}/{}", project.getRepoName(), updated, prContributions.size());
        return updated;
    }

    // ── PR의 변경 파일 목록을 가져와 평균 fileImportance를 반환 ───────────────

    private double calcImportanceForPr(Project project, String token, int pullNumber) {
        List<PullRequestFileDto> files = fetchAllPrFiles(project, token, pullNumber);

        // 파일별 경로 기반 중요도 수집 (제거된 파일 제외)
        List<Double> scores = new ArrayList<>();
        for (PullRequestFileDto file : files) {
            if ("removed".equals(file.status())) continue;

            // 이력 통계: 이 파일 경로와 관련된 커밋/기여 수를 DB에서 집계
            FileHistoryStats stats = buildHistoryStats(project.getId(), file.filename());
            double score = fileImportanceCalculator.calculate(file.filename(), stats);
            scores.add(score);
        }

        OptionalDouble avg = scores.stream().mapToDouble(Double::doubleValue).average();
        return avg.orElse(1.0);  // 변경 파일 없으면 기본값
    }

    /**
     * DB의 기존 기여 데이터로 파일 이력 통계를 근사한다.
     * - changeCount   : 이 파일이 포함된 PR의 총 기여 건수 (프로젝트 내 전체)
     * - contributorCount: 해당 파일에 기여한 고유 사용자 수 (PR 단위 근사)
     * 실제 파일 경로 단위의 커밋 이력은 GitHub API를 추가 호출해야 하지만,
     * 비용을 줄이기 위해 현재 DB 데이터로 대체한다.
     */
    private FileHistoryStats buildHistoryStats(Long projectId, String filename) {
        List<Contribution> allPrContribs =
                contributionRepository.findByProjectIdAndType(projectId, ContributionType.PR);

        int changeCount      = allPrContribs.size();  // PR 수 = 파일 변경 빈도의 근사값
        long contributorCount = allPrContribs.stream()
                .map(c -> c.getUser().getId())
                .distinct()
                .count();

        return new FileHistoryStats(changeCount, (int) contributorCount, 0);
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
