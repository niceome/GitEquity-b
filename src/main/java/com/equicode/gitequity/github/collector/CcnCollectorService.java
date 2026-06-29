package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.ContributionType;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.equity.ccn.CcnAnalyzer;
import com.equicode.gitequity.equity.ccn.CcnResult;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.FileContentDto;
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
 * PR별 변경 파일을 GitHub API로 가져와 CCN을 측정하고
 * 해당 PR 기여의 ccnScore를 업데이트한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CcnCollectorService {

    private final GithubApiClient githubApiClient;
    private final ContributionRepository contributionRepository;
    private final CcnAnalyzer ccnAnalyzer;

    @Transactional
    public int collect(Project project, String token) {
        if (!ccnAnalyzer.isLizardAvailable()) {
            log.warn("[CCN] lizard not found — install with: pip install lizard");
            return 0;
        }

        int updated = 0;
        updated += enrichType(project, token, ContributionType.PR);
        updated += enrichType(project, token, ContributionType.COMMIT);

        log.info("[CCN] project={} total updated={}", project.getRepoName(), updated);
        return updated;
    }

    private int enrichType(Project project, String token, ContributionType type) {
        List<Contribution> contributions =
                contributionRepository.findByProjectIdAndType(project.getId(), type);

        int updated = 0;
        for (Contribution contribution : contributions) {
            try {
                List<PullRequestFileDto> files = fetchFiles(project, token, type, contribution.getGithubId());
                double ccnScore = analyzeFiles(project, token, files);
                contribution.applyCcnScore(ccnScore, type.getWeight());
                updated++;
            } catch (Exception e) {
                log.warn("[CCN] failed to analyze {}={} project={}: {}",
                        type, contribution.getGithubId(), project.getRepoName(), e.getMessage());
            }
        }

        log.info("[CCN] project={} type={} updated={}/{}", project.getRepoName(), type, updated, contributions.size());
        return updated;
    }

    private List<PullRequestFileDto> fetchFiles(Project project, String token,
                                                ContributionType type, String githubId) {
        if (type == ContributionType.PR) {
            return fetchAllPrFiles(project, token, Integer.parseInt(githubId));
        }
        return githubApiClient.fetchCommitFiles(
                project.getRepoOwner(), project.getRepoName(), githubId, token);
    }

    // ── 파일 목록을 분석하여 평균 ccnScore 반환 ───────────────────────────────

    private double analyzeFiles(Project project, String token, List<PullRequestFileDto> files) {
        List<Double> scores = new ArrayList<>();
        for (PullRequestFileDto file : files) {
            if (!file.isAnalyzable()) continue;

            try {
                FileContentDto content = githubApiClient.fetchFileContent(
                        project.getRepoOwner(), project.getRepoName(), file.filename(), token);
                if (content == null) continue;

                CcnResult result = ccnAnalyzer.analyze(content.decodeContent(), file.filename());
                scores.add(result.ccnScore());
            } catch (Exception e) {
                log.debug("[CCN] skip file={}: {}", file.filename(), e.getMessage());
            }

            CollectorUtils.waitIfExhausted(null);
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
