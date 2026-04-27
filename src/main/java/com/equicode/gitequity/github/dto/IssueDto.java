package com.equicode.gitequity.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// GET /repos/{owner}/{repo}/issues?state=all 응답 단건
// ⚠️ 이슈 엔드포인트는 PR도 함께 반환 → pullRequest != null인 것은 PR이므로 제외
public record IssueDto(
        Long id,
        Integer number,
        String title,
        GitHubUser user,
        String state,           // "open" | "closed"
        String createdAt,
        @JsonProperty("pull_request") Object pullRequest  // PR이면 이 필드가 존재
) {
    // pull_request 필드가 있으면 실제로는 PR → 수집 대상에서 제외
    public boolean isPullRequest() {
        return pullRequest != null;
    }
}
