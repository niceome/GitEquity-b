package com.equicode.gitequity.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// GET /repos/{owner}/{repo}/pulls/{pull_number}/comments 응답 단건 (인라인 코드 리뷰 코멘트)
public record PullRequestReviewCommentDto(
        Long id,
        GitHubUser user,
        String body,
        @JsonProperty("pull_request_review_id") Long pullRequestReviewId,
        String createdAt
) {}
