package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.PrContribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.User;
import com.equicode.gitequity.equity.quality.CoAuthorParser;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.CommitDto;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.github.dto.RateLimitInfo;
import com.equicode.gitequity.repository.PrContributionRepository;
import com.equicode.gitequity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoAuthorAttributionCollectorServiceTest {

    private static final Project PROJECT = Project.builder()
            .id(1L).name("test-project").repoOwner("test-org").repoName("test-repo")
            .build();

    @Mock GithubApiClient githubApiClient;
    @Mock PrContributionRepository prContributionRepository;
    @Mock UserRepository userRepository;
    @Spy  CoAuthorParser coAuthorParser = new CoAuthorParser();

    @InjectMocks
    CoAuthorAttributionCollectorService service;

    private CommitDto commit(String message) {
        return new CommitDto("sha123", new CommitDto.CommitDetail(null, message), null);
    }

    private PagedResponse<CommitDto> commitsPage(CommitDto... commits) {
        return new PagedResponse<>(List.of(commits), false, new RateLimitInfo(5000, 0));
    }

    private PrContribution prContribution(Long authorGithubId, int prNumber) {
        return PrContribution.builder()
                .authorGithubId(authorGithubId)
                .prNumber(prNumber)
                .title("title")
                .isMerged(true)
                .additions(10)
                .deletions(0)
                .netLines(10)
                .build();
    }

    @Test
    @DisplayName("PR 번호와 Co-authored-by 이메일이 모두 매칭되면 공동작성자로 등록된다")
    void matchedCoAuthor_isApplied() {
        String message = "feat: add login page (#123)\n\nCo-authored-by: Bob Lee <bob@example.com>";
        when(githubApiClient.fetchCommits("test-org", "test-repo", 1, "token"))
                .thenReturn(commitsPage(commit(message)));

        PrContribution pr = prContribution(101L, 123);
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 123)).thenReturn(Optional.of(pr));

        User bob = User.builder().id(2L).githubId(102L).username("bob").email("bob@example.com").build();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(bob));

        int updated = service.collect(PROJECT, "token");

        assertThat(updated).isEqualTo(1);
        assertThat(pr.getCoAuthorGithubIds()).containsExactly(102L);
    }

    @Test
    @DisplayName("Co-authored-by 이메일이 등록된 사용자와 매칭되지 않으면 분할에서 제외된다")
    void unmatchedCoAuthorEmail_isSkipped() {
        String message = "feat: add login page (#123)\n\nCo-authored-by: Ghost <ghost@example.com>";
        when(githubApiClient.fetchCommits("test-org", "test-repo", 1, "token"))
                .thenReturn(commitsPage(commit(message)));

        PrContribution pr = prContribution(101L, 123);
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 123)).thenReturn(Optional.of(pr));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        int updated = service.collect(PROJECT, "token");

        assertThat(updated).isEqualTo(0);
        assertThat(pr.getCoAuthorGithubIds()).isNull();
    }

    @Test
    @DisplayName("PR 작성자 본인의 이메일이 Co-authored-by에 포함된 경우 분할 대상에서 제외한다")
    void selfCoAuthor_isExcluded() {
        String message = "feat: add login page (#123)\n\nCo-authored-by: Alice <alice@example.com>";
        when(githubApiClient.fetchCommits("test-org", "test-repo", 1, "token"))
                .thenReturn(commitsPage(commit(message)));

        PrContribution pr = prContribution(101L, 123);
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 123)).thenReturn(Optional.of(pr));

        User alice = User.builder().id(1L).githubId(101L).username("alice").email("alice@example.com").build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        int updated = service.collect(PROJECT, "token");

        assertThat(updated).isEqualTo(0);
        assertThat(pr.getCoAuthorGithubIds()).isNull();
    }

    @Test
    @DisplayName("여러 Co-authored-by 중 매칭된 사용자만 분할 대상에 포함된다")
    void multipleCoAuthors_onlyMatchedAreIncluded() {
        String message = "feat: add login page (#123)\n\n" +
                "Co-authored-by: Bob Lee <bob@example.com>\n" +
                "Co-authored-by: Ghost <ghost@example.com>";
        when(githubApiClient.fetchCommits("test-org", "test-repo", 1, "token"))
                .thenReturn(commitsPage(commit(message)));

        PrContribution pr = prContribution(101L, 123);
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 123)).thenReturn(Optional.of(pr));

        User bob = User.builder().id(2L).githubId(102L).username("bob").email("bob@example.com").build();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(bob));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        int updated = service.collect(PROJECT, "token");

        assertThat(updated).isEqualTo(1);
        assertThat(pr.getCoAuthorGithubIds()).containsExactly(102L);
    }

    @Test
    @DisplayName("메시지에 PR 번호가 없으면 처리하지 않는다")
    void noPrNumber_isSkipped() {
        when(githubApiClient.fetchCommits("test-org", "test-repo", 1, "token"))
                .thenReturn(commitsPage(commit("chore: misc cleanup")));

        int updated = service.collect(PROJECT, "token");

        assertThat(updated).isEqualTo(0);
        verifyNoInteractions(prContributionRepository, userRepository);
    }

    @Test
    @DisplayName("Co-authored-by 트레일러가 없으면 처리하지 않는다")
    void noCoAuthorTrailer_isSkipped() {
        when(githubApiClient.fetchCommits("test-org", "test-repo", 1, "token"))
                .thenReturn(commitsPage(commit("feat: add login page (#123)\n\nno trailers")));

        int updated = service.collect(PROJECT, "token");

        assertThat(updated).isEqualTo(0);
        verifyNoInteractions(prContributionRepository, userRepository);
    }

    @Test
    @DisplayName("PR 번호에 매칭되는 PrContribution이 없으면 처리하지 않는다")
    void noMatchingPrContribution_isSkipped() {
        String message = "feat: add login page (#999)\n\nCo-authored-by: Bob Lee <bob@example.com>";
        when(githubApiClient.fetchCommits("test-org", "test-repo", 1, "token"))
                .thenReturn(commitsPage(commit(message)));
        when(prContributionRepository.findByProjectIdAndPrNumber(1L, 999)).thenReturn(Optional.empty());

        int updated = service.collect(PROJECT, "token");

        assertThat(updated).isEqualTo(0);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("commit 정보가 없는 커밋은 건너뛴다")
    void nullCommitDetail_isSkipped() {
        CommitDto dto = new CommitDto("sha", null, null);
        when(githubApiClient.fetchCommits("test-org", "test-repo", 1, "token"))
                .thenReturn(commitsPage(dto));

        int updated = service.collect(PROJECT, "token");

        assertThat(updated).isEqualTo(0);
    }
}
