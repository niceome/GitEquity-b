package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.ReviewContribution;
import com.equicode.gitequity.equity.quality.ReviewSubstanceScorer;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.*;
import com.equicode.gitequity.repository.ReviewContributionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewSubstanceCollectorServiceTest {

    private static final Project PROJECT = Project.builder()
            .id(1L).name("test-project").repoOwner("test-org").repoName("test-repo")
            .build();

    private static final GitHubUser AUTHOR = new GitHubUser(100L, "alice", "User", null);
    private static final GitHubUser REVIEWER = new GitHubUser(200L, "bob", "User", null);
    private static final GitHubUser BOT = new GitHubUser(300L, "dependabot[bot]", "Bot", null);

    @Mock GithubApiClient githubApiClient;
    @Mock ReviewContributionRepository reviewContributionRepository;
    @Spy  ReviewSubstanceScorer reviewSubstanceScorer;

    @InjectMocks
    ReviewSubstanceCollectorService service;

    private PullRequestDto pr(int number, GitHubUser author) {
        return new PullRequestDto(1L, number, "Add feature", author, "closed",
                "2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z", false, List.of());
    }

    private PagedResponse<PullRequestDto> prPage(PullRequestDto... prs) {
        return new PagedResponse<>(List.of(prs), false, new RateLimitInfo(5000, 0));
    }

    private PagedResponse<PullRequestReviewDto> reviewPage(PullRequestReviewDto... reviews) {
        return new PagedResponse<>(List.of(reviews), false, new RateLimitInfo(5000, 0));
    }

    private PagedResponse<PullRequestReviewCommentDto> emptyCommentPage() {
        return new PagedResponse<>(List.of(), false, new RateLimitInfo(5000, 0));
    }

    // ── 완료 기준: 자기 PR 리뷰 제외 테스트 ───────────────────────────────────────

    @Test
    @DisplayName("자기 PR에 단 리뷰(author == reviewer)는 저장 대상에서 제외해야 한다")
    void selfReview_isExcluded() {
        PullRequestDto pr = pr(10, AUTHOR);
        when(githubApiClient.fetchPullRequests("test-org", "test-repo", 1, "token"))
                .thenReturn(prPage(pr));
        when(githubApiClient.fetchPullRequestReviewComments("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(emptyCommentPage());

        PullRequestReviewDto selfReview = new PullRequestReviewDto(
                1001L, AUTHOR, "looks good to me, my own work", "APPROVED", "2024-01-01T01:00:00Z");
        when(githubApiClient.fetchPullRequestReviews("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(reviewPage(selfReview));

        int saved = service.collect(PROJECT, "token");

        assertThat(saved).isEqualTo(0);
        verify(reviewContributionRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("봇 계정의 리뷰는 저장 대상에서 제외해야 한다")
    void botReview_isExcluded() {
        PullRequestDto pr = pr(10, AUTHOR);
        when(githubApiClient.fetchPullRequests("test-org", "test-repo", 1, "token"))
                .thenReturn(prPage(pr));
        when(githubApiClient.fetchPullRequestReviewComments("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(emptyCommentPage());

        PullRequestReviewDto botReview = new PullRequestReviewDto(
                1002L, BOT, "automated review", "COMMENTED", "2024-01-01T01:00:00Z");
        when(githubApiClient.fetchPullRequestReviews("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(reviewPage(botReview));

        int saved = service.collect(PROJECT, "token");

        assertThat(saved).isEqualTo(0);
    }

    @Test
    @DisplayName("다른 사용자의 \"LGTM\" 리뷰는 base×substance 점수로 저장되어야 한다")
    void otherUserLgtmReview_isSavedWithLowScore() {
        PullRequestDto pr = pr(10, AUTHOR);
        when(githubApiClient.fetchPullRequests("test-org", "test-repo", 1, "token"))
                .thenReturn(prPage(pr));
        when(githubApiClient.fetchPullRequestReviewComments("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(emptyCommentPage());

        PullRequestReviewDto review = new PullRequestReviewDto(
                2001L, REVIEWER, "LGTM", "APPROVED", "2024-01-01T01:00:00Z");
        when(githubApiClient.fetchPullRequestReviews("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(reviewPage(review));
        when(reviewContributionRepository.existsByProjectIdAndReviewId(1L, 2001L)).thenReturn(false);

        int saved = service.collect(PROJECT, "token");

        ArgumentCaptor<List<ReviewContribution>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewContributionRepository).saveAll(captor.capture());

        assertThat(saved).isEqualTo(1);
        ReviewContribution result = captor.getValue().get(0);
        assertThat(result.getReviewerGithubId()).isEqualTo(200L);
        assertThat(result.getPrNumber()).isEqualTo(10);
        assertThat(result.getScore()).isCloseTo(0.08, within(0.0001));  // 0.4 × 0.2
    }

    @Test
    @DisplayName("이미 수집된 리뷰(existsByProjectIdAndReviewId=true)는 건너뛰어야 한다")
    void alreadyCollectedReview_isSkipped() {
        PullRequestDto pr = pr(10, AUTHOR);
        when(githubApiClient.fetchPullRequests("test-org", "test-repo", 1, "token"))
                .thenReturn(prPage(pr));
        when(githubApiClient.fetchPullRequestReviewComments("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(emptyCommentPage());

        PullRequestReviewDto review = new PullRequestReviewDto(
                3001L, REVIEWER, "already collected", "APPROVED", "2024-01-01T01:00:00Z");
        when(githubApiClient.fetchPullRequestReviews("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(reviewPage(review));
        when(reviewContributionRepository.existsByProjectIdAndReviewId(1L, 3001L)).thenReturn(true);

        int saved = service.collect(PROJECT, "token");

        assertThat(saved).isEqualTo(0);
    }

    @Test
    @DisplayName("인라인 코드 코멘트가 같은 리뷰에 포함되면 합쳐진 본문으로 점수가 계산되어야 한다")
    void inlineComments_areCombinedIntoReviewScore() {
        PullRequestDto pr = pr(10, AUTHOR);
        when(githubApiClient.fetchPullRequests("test-org", "test-repo", 1, "token"))
                .thenReturn(prPage(pr));

        PullRequestReviewCommentDto inlineComment = new PullRequestReviewCommentDto(
                9001L, REVIEWER, "```java\nint x = 1;\n```", 4001L, "2024-01-01T01:00:00Z");
        when(githubApiClient.fetchPullRequestReviewComments("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(new PagedResponse<>(List.of(inlineComment), false, new RateLimitInfo(5000, 0)));

        PullRequestReviewDto review = new PullRequestReviewDto(
                4001L, REVIEWER, "see inline comment", "CHANGES_REQUESTED", "2024-01-01T01:00:00Z");
        when(githubApiClient.fetchPullRequestReviews("test-org", "test-repo", 10, 1, "token"))
                .thenReturn(reviewPage(review));

        int saved = service.collect(PROJECT, "token");

        ArgumentCaptor<List<ReviewContribution>> captor = ArgumentCaptor.forClass(List.class);
        verify(reviewContributionRepository).saveAll(captor.capture());

        assertThat(saved).isEqualTo(1);
        ReviewContribution result = captor.getValue().get(0);
        assertThat(result.isHasCodeBlock()).isTrue();
        // base(CHANGES_REQUESTED)=1.0 × substance(<20자: "see inline comment" 본문 자체는 짧지만
        // 인라인 코멘트가 합쳐져 substance 1.0 이상) + codeBlock 0.3
        assertThat(result.getScore()).isGreaterThan(1.0);
    }
}
