package com.equicode.gitequity.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// GET /repos/{owner}/{repo}/issues/comments 응답 단건 (이슈/PR 본문 코멘트, repo 전체 페이지네이션)
// issue_url 끝자리 숫자가 이슈/PR 번호 (PR도 issue 번호를 공유함)
public record IssueCommentDto(
        Long id,
        GitHubUser user,
        String body,
        String createdAt,
        @JsonProperty("issue_url") String issueUrl
) {
    public Integer issueNumber() {
        if (issueUrl == null) return null;
        String[] parts = issueUrl.split("/");
        return Integer.parseInt(parts[parts.length - 1]);
    }
}
