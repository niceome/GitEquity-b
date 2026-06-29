package com.equicode.gitequity.github;

import com.equicode.gitequity.auth.UserPrincipal;
import com.equicode.gitequity.common.exception.CustomException;
import com.equicode.gitequity.common.exception.ErrorCode;
import com.equicode.gitequity.common.response.ApiResponse;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.github.collector.CoAuthorAttributionCollectorService;
import com.equicode.gitequity.github.collector.CollectionResult;
import com.equicode.gitequity.github.collector.CommentCollectorService;
import com.equicode.gitequity.github.collector.CommunicationScoreService;
import com.equicode.gitequity.github.collector.ContributionCollectionService;
import com.equicode.gitequity.github.collector.DmmCollectorService;
import com.equicode.gitequity.github.collector.ReviewSubstanceCollectorService;
import com.equicode.gitequity.github.dto.*;
import com.equicode.gitequity.repository.ProjectRepository;
import com.equicode.gitequity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/github/test")
@RequiredArgsConstructor
public class GithubTestController {

    private final GithubApiClient githubApiClient;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ContributionCollectionService contributionCollectionService;
    private final DmmCollectorService dmmCollectorService;
    private final ReviewSubstanceCollectorService reviewSubstanceCollectorService;
    private final CommentCollectorService commentCollectorService;
    private final CommunicationScoreService communicationScoreService;
    private final CoAuthorAttributionCollectorService coAuthorAttributionCollectorService;

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
                "collection done: total=%d (commit=%d, pr=%d, review=%d, issue=%d, prContribution=%d)"
                        .formatted(result.total(), result.commits(), result.pullRequests(),
                                result.reviews(), result.issues(), result.prContributions()),
                result);
    }

    // Phase C 검증: 로컬에 clone된 repo의 main 브랜치 DMM을 분석하여 PR별 dmm_complexity 갱신
    @PostMapping("/dmm/{projectId}")
    public ApiResponse<Integer> testCollectDmm(
            @PathVariable Long projectId,
            @RequestParam String repoPath) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        int updated = dmmCollectorService.collect(project, repoPath);
        return ApiResponse.ok("dmm collection done: updated=%d".formatted(updated), updated);
    }

    // Phase C 검증: 리뷰 "실질성"(S5) 수집 — 자기 PR 리뷰/봇 리뷰 제외, base×substance+codeBlock 점수 산출
    @PostMapping("/review-substance/{projectId}")
    public ApiResponse<Integer> testCollectReviewSubstance(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = resolveToken(principal);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        int saved = reviewSubstanceCollectorService.collect(project, token);
        return ApiResponse.ok("review substance collection done: saved=%d".formatted(saved), saved);
    }

    // Phase C 검증: 이슈/PR 코멘트 "실질성"(S6, 커뮤니케이션 기여) raw 점수 수집 — 본인 이슈 자가 코멘트 제외
    @PostMapping("/comments/{projectId}")
    public ApiResponse<Integer> testCollectComments(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = resolveToken(principal);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        int saved = commentCollectorService.collect(project, token);
        return ApiResponse.ok("comment collection done: saved=%d".formatted(saved), saved);
    }

    // Phase C 검증: 사용자별 S6 점수 (PR netLines 합의 cap-ratio% 상한 적용 후)
    @GetMapping("/communication-score/{projectId}")
    public ApiResponse<Map<Long, Double>> testCommunicationScore(@PathVariable Long projectId) {
        Map<Long, Double> result = communicationScoreService.calculate(projectId);
        return ApiResponse.ok("communication score: %d users".formatted(result.size()), result);
    }

    // Phase E 검증: squash 커밋의 Co-authored-by 트레일러를 파싱하여 PR의 공동 작성자 분할 정보 갱신 (S12)
    @PostMapping("/co-authors/{projectId}")
    public ApiResponse<Integer> testCollectCoAuthors(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal principal) {

        String token = resolveToken(principal);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        int updated = coAuthorAttributionCollectorService.collect(project, token);
        return ApiResponse.ok("co-author attribution done: updated=%d".formatted(updated), updated);
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
