package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionCollectionService {

    private final CommitCollectorService commitCollectorService;
    private final PullRequestCollectorService pullRequestCollectorService;
    private final ReviewCollectorService reviewCollectorService;
    private final IssueCollectorService issueCollectorService;
    private final PrContributionCollectorService prContributionCollectorService;
    private final CcnCollectorService ccnCollectorService;
    private final FileImportanceCollectorService fileImportanceCollectorService;

    @Qualifier("collectorExecutor")
    private final Executor collectorExecutor;

    // 5가지 유형을 병렬 수집 후 CCN·파일중요도 순차 보강
    public CollectionResult collectAll(Project project, String token) {
        log.info("[Collection] start project={}", project.getRepoName());

        CompletableFuture<Integer> commitFuture = CompletableFuture.supplyAsync(
                () -> commitCollectorService.collect(project, token), collectorExecutor);
        CompletableFuture<Integer> prFuture = CompletableFuture.supplyAsync(
                () -> pullRequestCollectorService.collect(project, token), collectorExecutor);
        CompletableFuture<Integer> reviewFuture = CompletableFuture.supplyAsync(
                () -> reviewCollectorService.collect(project, token), collectorExecutor);
        CompletableFuture<Integer> issueFuture = CompletableFuture.supplyAsync(
                () -> issueCollectorService.collect(project, token), collectorExecutor);
        CompletableFuture<Integer> prContributionFuture = CompletableFuture.supplyAsync(
                () -> prContributionCollectorService.collect(project, token), collectorExecutor);

        CompletableFuture.allOf(commitFuture, prFuture, reviewFuture, issueFuture, prContributionFuture).join();

        CollectionResult result = new CollectionResult(
                commitFuture.join(),
                prFuture.join(),
                reviewFuture.join(),
                issueFuture.join(),
                prContributionFuture.join());

        log.info("[Collection] done project={} total={} (commit={}, pr={}, review={}, issue={}, prContribution={})",
                project.getRepoName(), result.total(),
                result.commits(), result.pullRequests(), result.reviews(), result.issues(), result.prContributions());

        // 기여 저장 완료 후 CCN·파일중요도 보강 (COMMIT + PR 대상)
        fileImportanceCollectorService.collect(project, token);
        ccnCollectorService.collect(project, token);

        return result;
    }

    // 비동기 fire-and-forget (웹훅/스케줄러에서 호출)
    @Async("collectorExecutor")
    public CompletableFuture<CollectionResult> collectAllAsync(Project project, String token) {
        try {
            return CompletableFuture.completedFuture(collectAll(project, token));
        } catch (Exception e) {
            log.error("[Collection] failed project={} error={}", project.getRepoName(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
