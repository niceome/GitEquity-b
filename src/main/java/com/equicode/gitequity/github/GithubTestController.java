package com.equicode.gitequity.github;

import com.equicode.gitequity.auth.UserPrincipal;
import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.common.exception.ErrorCode;
import com.equicode.gitequity.common.response.ApiResponse;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.github.collector.CollectionResult;
import com.equicode.gitequity.github.collector.ContributionCollectionService;
import com.equicode.gitequity.github.dto.*;
import com.equicode.gitequity.repository.ProjectRepository;
import com.equicode.gitequity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github/test")
@RequiredArgsConstructor
public class GithubTestController {

    private final GithubApiClient githubApiClient;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ContributionCollectionService contributionCollectionService;

    @GetMapping("/commits")
    public ApiResponse<PagedResponse<CommitDto>> testCommits(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = resolveToken(principal);
        PagedResponse<CommitDto> response = githubApiClient.fetchCommits(owner, repo, page, token);
        return ApiResponse.ok(
                "commits fetched: %d items, hasNext=%s, rateLimit=%d"
                        .formatted(response.items().size(), response.hasNext(),
                                response.rateLimitInfo().remaining()),
                response);
    }

    @GetMapping("/pulls")
    public ApiResponse<PagedResponse<PullRequestDto>> testPullRequests(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = resolveToken(principal);
        PagedResponse<PullRequestDto> response = githubApiClient.fetchPullRequests(owner, repo, page, token);
        return ApiResponse.ok(
                "pulls fetched: %d items, hasNext=%s, rateLimit=%d"
                        .formatted(response.items().size(), response.hasNext(),
                                response.rateLimitInfo().remaining()),
                response);
    }

    @GetMapping("/pulls/{pullNumber}/reviews")
    public ApiResponse<PagedResponse<PullRequestReviewDto>> testReviews(
            @RequestParam String owner,
            @RequestParam String repo,
            @PathVariable int pullNumber,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = resolveToken(principal);
        PagedResponse<PullRequestReviewDto> response =
                githubApiClient.fetchPullRequestReviews(owner, repo, pullNumber, page, token);
        return ApiResponse.ok(
                "reviews fetched: %d items, hasNext=%s, rateLimit=%d"
                        .formatted(response.items().size(), response.hasNext(),
                                response.rateLimitInfo().remaining()),
                response);
    }

    @GetMapping("/issues")
    public ApiResponse<PagedResponse<IssueDto>> testIssues(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = resolveToken(principal);
        PagedResponse<IssueDto> response = githubApiClient.fetchIssues(owner, repo, page, token);
        long issueCount = response.items().stream().filter(i -> !i.isPullRequest()).count();
        return ApiResponse.ok(
                "issues fetched: %d total, %d pure issues (non-PR), hasNext=%s, rateLimit=%d"
                        .formatted(response.items().size(), issueCount, response.hasNext(),
                                response.rateLimitInfo().remaining()),
                response);
    }

    // Phase C 검증: 프로젝트 전체 기여 수집 (동기)
    @PostMapping("/collect/{projectId}")
    public ApiResponse<CollectionResult> testCollectAll(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = resolveToken(principal);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        CollectionResult result = contributionCollectionService.collectAll(project, token);
        return ApiResponse.ok(
                "collection done: total=%d (commit=%d, pr=%d, review=%d, issue=%d)"
                        .formatted(result.total(), result.commits(), result.pullRequests(),
                                result.reviews(), result.issues()),
                result);
    }

    private String resolveToken(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getAccessToken() == null) {
            throw new CustomException(ErrorCode.GITHUB_API_ERROR);
        }
        return user.getAccessToken();
    }
}
