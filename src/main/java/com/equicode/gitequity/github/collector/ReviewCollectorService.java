package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.Contribution;
import com.equicode.gitequity.domain.ContributionType;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.github.dto.PullRequestDto;
import com.equicode.gitequity.github.dto.PullRequestReviewDto;
import com.equicode.gitequity.repository.ContributionRepository;
import com.equicode.gitequity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCollectorService {

    private static final Set<String> MEANINGFUL_STATES = Set.of("APPROVED", "CHANGES_REQUESTED");

    private final GithubApiClient githubApiClient;
    private final ContributionRepository contributionRepository;
    private final UserRepository userRepository;

    // PR 목록을 순회하며 각 PR의 리뷰를 수집
    // APPROVED / CHANGES_REQUESTED 만 기여로 인정 (COMMENTED, DISMISSED 제외)
    @Transactional
    public int collect(Project project, String token) {
        List<Contribution> toSave = new ArrayList<>();

        // PR 전체 목록 순회
        int prPage = 1;
        while (true) {
            PagedResponse<PullRequestDto> prResponse = githubApiClient.fetchPullRequests(
                    project.getRepoOwner(), project.getRepoName(), prPage, token);

            for (PullRequestDto pr : prResponse.items()) {
                if (Boolean.TRUE.equals(pr.draft())) continue;

                collectReviewsForPr(project, token, pr.number(), toSave);
            }

            CollectorUtils.waitIfExhausted(prResponse.rateLimitInfo());
            if (!prResponse.hasNext()) break;
            prPage++;
        }

        contributionRepository.saveAll(toSave);
        log.info("[Review] project={} saved={}", project.getRepoName(), toSave.size());
        return toSave.size();
    }

    private void collectReviewsForPr(Project project, String token, int pullNumber,
                                     List<Contribution> toSave) {
        int page = 1;
        while (true) {
            PagedResponse<PullRequestReviewDto> response = githubApiClient.fetchPullRequestReviews(
                    project.getRepoOwner(), project.getRepoName(), pullNumber, page, token);

            for (PullRequestReviewDto dto : response.items()) {
                if (dto.user() == null || dto.user().isBot()) continue;
                if (!MEANINGFUL_STATES.contains(dto.state())) continue;

                Optional<User> userOpt = userRepository.findByUsername(dto.user().login());
                if (userOpt.isEmpty()) continue;

                String githubId = dto.id().toString();
                if (contributionRepository.existsByProjectIdAndTypeAndGithubId(
                        project.getId(), ContributionType.REVIEW, githubId)) continue;

                toSave.add(Contribution.builder()
                        .user(userOpt.get())
                        .project(project)
                        .type(ContributionType.REVIEW)
                        .githubId(githubId)
                        .count(1)
                        .ccnScore(1.0)
                        .fileImportance(1.0)
                        .rawScore(ContributionType.REVIEW.getWeight())
                        .occurredAt(CollectorUtils.parseIso(dto.submittedAt()))
                        .build());
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }
    }
}
