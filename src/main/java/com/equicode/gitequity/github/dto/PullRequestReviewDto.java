package com.equicode.gitequity.github.dto;

// GET /repos/{owner}/{repo}/pulls/{pull_number}/reviews 응답 단건
public record PullRequestReviewDto(
        Long id,
        GitHubUser user,
        String state,           // "APPROVED" | "CHANGES_REQUESTED" | "COMMENTED" | "DISMISSED"
        String submittedAt
) {}
