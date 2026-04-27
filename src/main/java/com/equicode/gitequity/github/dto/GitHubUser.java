package com.equicode.gitequity.github.dto;

// GitHub API의 user 오브젝트
// type 필드로 Bot 계정(dependabot, github-actions[bot] 등) 판별
public record GitHubUser(
        Long id,
        String login,
        String type,     // "User" | "Bot" | "Organization"
        String avatarUrl
) {
    // Bot 계정 필터링 — type 필드 또는 로그인명 패턴으로 판별
    public boolean isBot() {
        if ("Bot".equalsIgnoreCase(type)) return true;
        if (login == null) return false;
        return login.endsWith("[bot]") || login.equals("dependabot");
    }
}
