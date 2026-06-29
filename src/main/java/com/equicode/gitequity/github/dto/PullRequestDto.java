package com.equicode.gitequity.github.dto;

import java.util.List;

// GET /repos/{owner}/{repo}/pulls?state=all|closed 응답 단건
public record PullRequestDto(
        Long id,
        Integer number,
        String title,
        GitHubUser user,
        String state,           // "open" | "closed"
        String createdAt,
        String mergedAt,        // null이면 미병합
        Boolean draft,
        List<LabelDto> labels
) {
    public boolean isMerged() {
        return mergedAt != null;
    }

    public List<String> labelNames() {
        return labels != null ? labels.stream().map(LabelDto::name).toList() : List.of();
    }

    // GitHub label 오브젝트 — name만 사용
    public record LabelDto(String name) {}
}
