package com.equicode.gitequity.github.dto;

import java.util.List;

// GET /repos/{owner}/{repo}/commits/{sha} 응답 (파일 목록 추출용)
public record CommitFilesDto(
        String sha,
        List<PullRequestFileDto> files
) {
    public List<PullRequestFileDto> safeFiles() {
        return files != null ? files : List.of();
    }
}
