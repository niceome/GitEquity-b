package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.PrContribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.equity.quality.DensityCalculator;
import com.equicode.gitequity.equity.quality.DensityResult;
import com.equicode.gitequity.equity.quality.PrType;
import com.equicode.gitequity.equity.quality.PrTypeClassifier;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.github.dto.PullRequestDto;
import com.equicode.gitequity.github.dto.PullRequestFileDto;
import com.equicode.gitequity.repository.PrContributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * PR 단위로 "최종 diff 실질 변경량"을 수집한다.
 *
 * - state=closed PR을 전부 수집 (병합 PR + closed-unmerged PR)
 * - is_merged=false (closed-unmerged) PR은 "탐색 기여"로 별도 저장 (점수 가중치는 후속 단계에서 적용)
 * - 변경 파일의 additions/deletions와 patch 기반 density(net/gross)를 함께 집계하고,
 *   patch 본문 자체는 사용 후 즉시 버린다 (용량 문제)
 * - PR 제목/라벨로 PrType(FEAT/FIX/...)을 분류하여 pr_type 컬럼에 저장한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrContributionCollectorService {

    private final GithubApiClient githubApiClient;
    private final PrContributionRepository prContributionRepository;
    private final PrTypeClassifier prTypeClassifier;
    private final DensityCalculator densityCalculator;

    @Transactional
    public int collect(Project project, String token) {
        List<PrContribution> toSave = new ArrayList<>();
        int page = 1;

        while (true) {
            PagedResponse<PullRequestDto> response = githubApiClient.fetchClosedPullRequests(
                    project.getRepoOwner(), project.getRepoName(), page, token);

            for (PullRequestDto dto : response.items()) {
                if (dto.user() == null || dto.user().isBot()) continue;
                if (Boolean.TRUE.equals(dto.draft())) continue;
                if (prContributionRepository.existsByProjectIdAndPrNumber(project.getId(), dto.number())) continue;

                FileAnalysis analysis = analyzeFiles(project, token, dto.number());
                PrType prType = prTypeClassifier.classify(dto.labelNames(), dto.title());

                toSave.add(PrContribution.builder()
                        .project(project)
                        .authorGithubId(dto.user().id())
                        .prNumber(dto.number())
                        .title(dto.title())
                        .labels(dto.labelNames())
                        .isMerged(dto.isMerged())
                        .mergedAt(dto.mergedAt() != null ? CollectorUtils.parseIso(dto.mergedAt()) : null)
                        .additions(analysis.additions())
                        .deletions(analysis.deletions())
                        .netLines(analysis.density().netLines())
                        .density(analysis.density().density())
                        .prType(prType.name())
                        .build());
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            CollectorUtils.warnIfRateLimitLow(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }

        prContributionRepository.saveAll(toSave);
        log.info("[PrContribution] project={} saved={}", project.getRepoName(), toSave.size());
        return toSave.size();
    }

    // ── PR 변경 파일의 additions/deletions/density 합산 (patch 본문은 사용 후 버림) ─────

    private FileAnalysis analyzeFiles(Project project, String token, int pullNumber) {
        int additions = 0;
        int deletions = 0;
        DensityResult density = DensityResult.zero();
        int page = 1;

        while (true) {
            PagedResponse<PullRequestFileDto> response = githubApiClient.fetchPullRequestFiles(
                    project.getRepoOwner(), project.getRepoName(), pullNumber, page, token);

            for (PullRequestFileDto file : response.items()) {
                additions += file.additions();
                deletions += file.deletions();
                density = density.plus(densityCalculator.analyze(file.patch()));
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            CollectorUtils.warnIfRateLimitLow(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }

        return new FileAnalysis(additions, deletions, density);
    }

    private record FileAnalysis(int additions, int deletions, DensityResult density) {}
}
