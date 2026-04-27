package com.equicode.gitequity.github.dto;

// GET /repos/{owner}/{repo}/pulls?state=all 응답 단건
public record PullRequestDto(
        Long id,
        Integer number,
        String title,
        GitHubUser user,
        String state,           // "open" | "closed"
        String createdAt,
        String mergedAt,        // null이면 미병합
        Boolean draft
) {
    public boolean isMerged() {
        return mergedAt != null;
    }
}
