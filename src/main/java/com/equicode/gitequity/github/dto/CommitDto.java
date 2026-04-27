package com.equicode.gitequity.github.dto;

// GET /repos/{owner}/{repo}/commits 응답 단건
// author 필드는 GitHub 계정이 없는 커밋의 경우 null 가능
public record CommitDto(
        String sha,
        CommitDetail commit,
        GitHubUser author      // GitHub 계정 (null = 미등록 이메일)
) {
    public record CommitDetail(
            CommitAuthor author
    ) {}

    public record CommitAuthor(
            String name,
            String email,
            String date         // ISO 8601: "2024-01-01T00:00:00Z"
    ) {}
}
