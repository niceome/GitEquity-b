package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.CommentContribution;
import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.equity.quality.CommentScorer;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.IssueCommentDto;
import com.equicode.gitequity.github.dto.IssueDto;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.repository.CommentContributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이슈/PR 코멘트 "개수"가 아닌 "실질성"(S6, 커뮤니케이션 기여)을 평가하여
 * comment_contributions에 raw 점수로 저장한다 (사용자별 cap은 CommunicationScoreService가 적용).
 *
 * - repo 전체 이슈/PR 코멘트(/issues/comments)를 페이지네이션하며 수집한다.
 * - 본인이 연 이슈/PR에 단 코멘트(author == issue opener)는 제외한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentCollectorService {

    private final GithubApiClient githubApiClient;
    private final CommentContributionRepository commentContributionRepository;
    private final CommentScorer commentScorer;

    @Transactional
    public int collect(Project project, String token) {
        Map<Integer, Long> issueAuthors = collectIssueAuthors(project, token);

        List<CommentContribution> toSave = new ArrayList<>();
        int page = 1;

        while (true) {
            PagedResponse<IssueCommentDto> response = githubApiClient.fetchIssueComments(
                    project.getRepoOwner(), project.getRepoName(), page, token);

            for (IssueCommentDto dto : response.items()) {
                if (dto.user() == null || dto.user().isBot()) continue;

                Integer issueNumber = dto.issueNumber();
                Long authorId = dto.user().id();

                Long issueAuthorId = issueAuthors.get(issueNumber);
                if (authorId.equals(issueAuthorId)) continue; // 본인이 연 이슈/PR에 단 코멘트 제외

                if (commentContributionRepository.existsByProjectIdAndCommentId(project.getId(), dto.id())) continue;

                String body = dto.body() != null ? dto.body() : "";
                toSave.add(CommentContribution.builder()
                        .project(project)
                        .authorGithubId(authorId)
                        .issueNumber(issueNumber)
                        .commentId(dto.id())
                        .contentLength(body.trim().length())
                        .hasCodeBlock(commentScorer.hasCodeBlock(body))
                        .score(commentScorer.score(body))
                        .createdAtGithub(dto.createdAt() != null ? CollectorUtils.parseIso(dto.createdAt()) : null)
                        .build());
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }

        commentContributionRepository.saveAll(toSave);
        log.info("[Comment] project={} saved={}", project.getRepoName(), toSave.size());
        return toSave.size();
    }

    // 이슈/PR 번호 → 작성자 GitHub ID (자기 코멘트 필터링용)
    // GitHub /issues 엔드포인트는 state=all일 때 이슈와 PR을 모두 반환한다
    private Map<Integer, Long> collectIssueAuthors(Project project, String token) {
        Map<Integer, Long> authors = new HashMap<>();
        int page = 1;

        while (true) {
            PagedResponse<IssueDto> response = githubApiClient.fetchIssues(
                    project.getRepoOwner(), project.getRepoName(), page, token);

            for (IssueDto dto : response.items()) {
                if (dto.user() != null) {
                    authors.put(dto.number(), dto.user().id());
                }
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }

        return authors;
    }
}
