package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.domain.Project;
import com.equicode.gitequity.domain.ReviewContribution;
import com.equicode.gitequity.equity.quality.ReviewSubstanceScorer;
import com.equicode.gitequity.github.GithubApiClient;
import com.equicode.gitequity.github.dto.PagedResponse;
import com.equicode.gitequity.github.dto.PullRequestDto;
import com.equicode.gitequity.github.dto.PullRequestReviewCommentDto;
import com.equicode.gitequity.github.dto.PullRequestReviewDto;
import com.equicode.gitequity.repository.ReviewContributionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 리뷰 "개수"가 아닌 "실질성"(S5)을 평가하여 review_contributions에 저장한다.
 *
 * - PR마다 리뷰(/reviews)와 인라인 코드 코멘트(/comments)를 함께 수집한다.
 * - 인라인 코멘트는 pull_request_review_id로 소속 리뷰에 묶어 본문과 합산한 뒤
 *   ReviewSubstanceScorer로 점수를 산출한다.
 * - 자기 PR에 단 리뷰(author == reviewer)와 bot 리뷰는 제외한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSubstanceCollectorService {

    private final GithubApiClient githubApiClient;
    private final ReviewContributionRepository reviewContributionRepository;
    private final ReviewSubstanceScorer reviewSubstanceScorer;

    @Transactional
    public int collect(Project project, String token) {
        List<ReviewContribution> toSave = new ArrayList<>();

        int prPage = 1;
        while (true) {
            PagedResponse<PullRequestDto> prResponse = githubApiClient.fetchPullRequests(
                    project.getRepoOwner(), project.getRepoName(), prPage, token);

            for (PullRequestDto pr : prResponse.items()) {
                if (pr.user() == null) continue;
                if (Boolean.TRUE.equals(pr.draft())) continue;

                collectForPr(project, token, pr, toSave);
            }

            CollectorUtils.waitIfExhausted(prResponse.rateLimitInfo());
            if (!prResponse.hasNext()) break;
            prPage++;
        }

        reviewContributionRepository.saveAll(toSave);
        log.info("[ReviewSubstance] project={} saved={}", project.getRepoName(), toSave.size());
        return toSave.size();
    }

    private void collectForPr(Project project, String token, PullRequestDto pr, List<ReviewContribution> toSave) {
        Long authorId = pr.user().id();
        Map<Long, StringBuilder> commentsByReview = collectInlineCommentsByReview(project, token, pr.number());

        int page = 1;
        while (true) {
            PagedResponse<PullRequestReviewDto> response = githubApiClient.fetchPullRequestReviews(
                    project.getRepoOwner(), project.getRepoName(), pr.number(), page, token);

            for (PullRequestReviewDto review : response.items()) {
                if (review.user() == null || review.user().isBot()) continue;
                if (authorId.equals(review.user().id())) continue; // 자기 PR 리뷰 제외

                if (reviewContributionRepository.existsByProjectIdAndReviewId(project.getId(), review.id())) continue;

                String inlineComments = commentsByReview
                        .getOrDefault(review.id(), new StringBuilder())
                        .toString();
                String combinedText = (review.body() != null ? review.body() : "") + "\n" + inlineComments;

                toSave.add(ReviewContribution.builder()
                        .project(project)
                        .reviewerGithubId(review.user().id())
                        .prNumber(pr.number())
                        .reviewId(review.id())
                        .state(review.state())
                        .contentLength(combinedText.trim().length())
                        .hasCodeBlock(reviewSubstanceScorer.hasCodeBlock(combinedText))
                        .score(reviewSubstanceScorer.score(review.state(), combinedText))
                        .submittedAt(review.submittedAt() != null ? CollectorUtils.parseIso(review.submittedAt()) : null)
                        .build());
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }
    }

    // 인라인 코드 코멘트를 소속 리뷰(pull_request_review_id) 기준으로 본문을 합친다
    private Map<Long, StringBuilder> collectInlineCommentsByReview(Project project, String token, int prNumber) {
        Map<Long, StringBuilder> commentsByReview = new HashMap<>();
        int page = 1;

        while (true) {
            PagedResponse<PullRequestReviewCommentDto> response = githubApiClient.fetchPullRequestReviewComments(
                    project.getRepoOwner(), project.getRepoName(), prNumber, page, token);

            for (PullRequestReviewCommentDto comment : response.items()) {
                if (comment.pullRequestReviewId() == null) continue;
                commentsByReview
                        .computeIfAbsent(comment.pullRequestReviewId(), k -> new StringBuilder())
                        .append(comment.body() != null ? comment.body() : "")
                        .append('\n');
            }

            CollectorUtils.waitIfExhausted(response.rateLimitInfo());
            if (!response.hasNext()) break;
            page++;
        }

        return commentsByReview;
    }
}
