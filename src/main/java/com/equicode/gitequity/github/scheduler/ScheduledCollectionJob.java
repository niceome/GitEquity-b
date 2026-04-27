package com.equicode.gitequity.github.scheduler;

import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.github.collector.CollectionResult;
import com.equicode.gitequity.github.collector.ContributionCollectionService;
import com.equicode.gitequity.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledCollectionJob {

    private final ProjectRepository projectRepository;
    private final ContributionCollectionService contributionCollectionService;

    // 매일 새벽 3시 KST (= 18:00 UTC)
    // application.yml: github.collection.cron 으로 오버라이드 가능
    @Scheduled(cron = "${github.collection.cron:0 0 18 * * *}")
    @Transactional(readOnly = true)
    public void runNightlyCollection() {
        List<Project> projects = projectRepository.findAll();
        if (projects.isEmpty()) {
            log.info("[Scheduler] no projects to collect");
            return;
        }

        log.info("[Scheduler] nightly collection start — {} projects", projects.size());

        // 프로젝트별 비동기 수집 시작 후 전체 완료 대기
        List<CompletableFuture<CollectionResult>> futures = projects.stream()
                .map(project -> {
                    String token = resolveToken(project);
                    if (token == null) {
                        log.warn("[Scheduler] skip project={} — owner has no access token",
                                project.getId());
                        return CompletableFuture.<CollectionResult>completedFuture(null);
                    }
                    return contributionCollectionService.collectAllAsync(project, token)
                            .exceptionally(ex -> {
                                log.error("[Scheduler] collection failed project={} error={}",
                                        project.getRepoName(), ex.getMessage());
                                return null;
                            });
                })
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long succeeded = futures.stream()
                .map(CompletableFuture::join)
                .filter(r -> r != null)
                .count();

        log.info("[Scheduler] nightly collection done — {}/{} projects succeeded",
                succeeded, projects.size());
    }

    private String resolveToken(Project project) {
        // LAZY 관계지만 @Transactional(readOnly=true) 내에서 호출되므로 안전
        if (project.getOwner() == null) return null;
        return project.getOwner().getAccessToken();
    }
}
