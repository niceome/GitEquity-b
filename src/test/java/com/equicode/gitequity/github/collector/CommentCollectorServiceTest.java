package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.CommentContribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.equity.quality.CommentScorer;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.*;
import com.equicode.gitequity.repository.CommentContributionRepository;
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
class CommentCollectorServiceTest {

    private static final Project PROJECT = Project.builder()
            .id(1L).name("test-project").repoOwner("test-org").repoName("test-repo")
            .build();

    private static final GitHubUser AUTHOR = new GitHubUser(100L, "alice", "User", null);
    private static final GitHubUser OTHER = new GitHubUser(200L, "bob", "User", null);
    private static final GitHubUser BOT = new GitHubUser(300L, "dependabot[bot]", "Bot", null);

    private static final String ISSUE_URL_5 = "https://api.github.com/repos/test-org/test-repo/issues/5";

    @Mock GithubApiClient githubApiClient;
    @Mock CommentContributionRepository commentContributionRepository;
    @Spy  CommentScorer commentScorer;

    @InjectMocks
    CommentCollectorService service;

    private IssueDto issue(int number, GitHubUser author) {
        return new IssueDto(1L, number, "Some issue", author, "open", "2024-01-01T00:00:00Z", null);
    }

    private PagedResponse<IssueDto> issuesPage(IssueDto... issues) {
        return new PagedResponse<>(List.of(issues), false, new RateLimitInfo(5000, 0));
    }

    private PagedResponse<IssueCommentDto> commentsPage(IssueCommentDto... comments) {
        return new PagedResponse<>(List.of(comments), false, new RateLimitInfo(5000, 0));
    }

    @Test
    @DisplayName("본인이 연 이슈에 단 코멘트는 제외해야 한다")
    void selfIssueComment_isExcluded() {
        when(githubApiClient.fetchIssues("test-org", "test-repo", 1, "token"))
                .thenReturn(issuesPage(issue(5, AUTHOR)));

        IssueCommentDto selfComment = new IssueCommentDto(
                9001L, AUTHOR, "thanks for the report, will look into it", "2024-01-01T01:00:00Z", ISSUE_URL_5);
        when(githubApiClient.fetchIssueComments("test-org", "test-repo", 1, "token"))
                .thenReturn(commentsPage(selfComment));

        int saved = service.collect(PROJECT, "token");

        assertThat(saved).isEqualTo(0);
        verify(commentContributionRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("봇 계정의 코멘트는 제외해야 한다")
    void botComment_isExcluded() {
        when(githubApiClient.fetchIssues("test-org", "test-repo", 1, "token"))
                .thenReturn(issuesPage(issue(5, AUTHOR)));

        IssueCommentDto botComment = new IssueCommentDto(
                9002L, BOT, "automated comment", "2024-01-01T01:00:00Z", ISSUE_URL_5);
        when(githubApiClient.fetchIssueComments("test-org", "test-repo", 1, "token"))
                .thenReturn(commentsPage(botComment));

        int saved = service.collect(PROJECT, "token");

        assertThat(saved).isEqualTo(0);
    }

    @Test
    @DisplayName("다른 사용자의 짧은 코멘트(<30자)는 0.1점으로 저장되어야 한다")
    void otherUserShortComment_isSavedWithMinimumScore() {
        when(githubApiClient.fetchIssues("test-org", "test-repo", 1, "token"))
                .thenReturn(issuesPage(issue(5, AUTHOR)));

        IssueCommentDto comment = new IssueCommentDto(
                9003L, OTHER, "ok thanks!", "2024-01-01T01:00:00Z", ISSUE_URL_5);
        when(githubApiClient.fetchIssueComments("test-org", "test-repo", 1, "token"))
                .thenReturn(commentsPage(comment));
        when(commentContributionRepository.existsByProjectIdAndCommentId(1L, 9003L)).thenReturn(false);

        int saved = service.collect(PROJECT, "token");

        ArgumentCaptor<List<CommentContribution>> captor = ArgumentCaptor.forClass(List.class);
        verify(commentContributionRepository).saveAll(captor.capture());

        assertThat(saved).isEqualTo(1);
        CommentContribution result = captor.getValue().get(0);
        assertThat(result.getAuthorGithubId()).isEqualTo(200L);
        assertThat(result.getIssueNumber()).isEqualTo(5);
        assertThat(result.getScore()).isCloseTo(0.1, within(0.0001));
        assertThat(result.isHasCodeBlock()).isFalse();
    }

    @Test
    @DisplayName("코드블록을 포함한 코멘트는 0.5 보너스가 가산되어야 한다")
    void commentWithCodeBlock_getsBonus() {
        when(githubApiClient.fetchIssues("test-org", "test-repo", 1, "token"))
                .thenReturn(issuesPage(issue(5, AUTHOR)));

        String body = "a".repeat(60) + "\n```\ncode\n```";
        IssueCommentDto comment = new IssueCommentDto(
                9004L, OTHER, body, "2024-01-01T01:00:00Z", ISSUE_URL_5);
        when(githubApiClient.fetchIssueComments("test-org", "test-repo", 1, "token"))
                .thenReturn(commentsPage(comment));
        when(commentContributionRepository.existsByProjectIdAndCommentId(1L, 9004L)).thenReturn(false);

        int saved = service.collect(PROJECT, "token");

        ArgumentCaptor<List<CommentContribution>> captor = ArgumentCaptor.forClass(List.class);
        verify(commentContributionRepository).saveAll(captor.capture());

        assertThat(saved).isEqualTo(1);
        CommentContribution result = captor.getValue().get(0);
        assertThat(result.isHasCodeBlock()).isTrue();
        // 길이 73자 → 73/300=0.2433... + 코드블록 보너스 0.5 = 0.7433...
        assertThat(result.getScore()).isCloseTo(0.7433, within(0.001));
    }

    @Test
    @DisplayName("이미 수집된 코멘트(existsByProjectIdAndCommentId=true)는 건너뛰어야 한다")
    void alreadyCollectedComment_isSkipped() {
        when(githubApiClient.fetchIssues("test-org", "test-repo", 1, "token"))
                .thenReturn(issuesPage(issue(5, AUTHOR)));

        IssueCommentDto comment = new IssueCommentDto(
                9005L, OTHER, "already collected comment text", "2024-01-01T01:00:00Z", ISSUE_URL_5);
        when(githubApiClient.fetchIssueComments("test-org", "test-repo", 1, "token"))
                .thenReturn(commentsPage(comment));
        when(commentContributionRepository.existsByProjectIdAndCommentId(1L, 9005L)).thenReturn(true);

        int saved = service.collect(PROJECT, "token");

        assertThat(saved).isEqualTo(0);
    }
}
