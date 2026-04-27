package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.ContributionType;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.github.dto.PullRequestDto;
import com.equicode.gitequity.repository.ContributionRepository;
import com.equicode.gitequity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PullRequestCollectorService {

    private final GithubApiClient githubApiClient;
    private final ContributionRepository contributionRepository;
    private final UserRepository userRepository;

    // draft PR 및 미병합 PR은 기여로 인정하지 않음
    @Transactional
    public int collect(Project project, String token) {
        List<Contribution> toSave = new ArrayList<>();
        int page = 1;

        while (true) {
            PagedResponse<PullRequestDto> response = githubApiClient.fetchPullRequests(
                    project.getRepoOwner(), project.getRepoName(), page, token);

            for (PullRequestDto dto : response.items()) {
                if (dto.user() == null || dto.user().isBot()) continue;
                if (Boolean.TRUE.equals(dto.draft())) continue;
                if (!dto.isMerged()) continue;

                Optional<User> userOpt = userRepository.findByUsername(dto.user().login());
                if (userOpt.isEmpty()) continue;

                String githubId = dto.number().toString();
                if (contributionRepository.existsByProjectIdAndTypeAndGithubId(
                        project.getId(), ContributionType.PR, githubId)) continue;

                toSave.add(Contribution.builder()
                        .user(userOpt.get())
                        .project(project)
                        .type(ContributionType.PR)
                        .githubId(githubId)
                        .count(1)
                        .ccnScore(1.0)
                        .fileImportance(1.0)
                        .rawScore(ContributionType.PR.getWeight())
                        .occurredAt(CollectorUtils.parseIso(dto.mergedAt()))
                        .build());
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }

        contributionRepository.saveAll(toSave);
        log.info("[PR] project={} saved={}", project.getRepoName(), toSave.size());
        return toSave.size();
    }
}
