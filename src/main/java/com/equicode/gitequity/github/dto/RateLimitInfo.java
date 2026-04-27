package com.equicode.gitequity.github.dto;

// GitHub API 응답 헤더의 Rate Limit 정보
// X-RateLimit-Remaining: 남은 호출 횟수
// X-RateLimit-Reset: 리셋 시각 (Unix epoch seconds)
public record RateLimitInfo(
        int remaining,
        long resetEpochSeconds
) {
    public boolean isExhausted() {
        return remaining <= 0;
    }

    // Rate limit 리셋까지 대기해야 할 밀리초 (1초 여유 포함)
    public long waitMillisUntilReset() {
        long resetMs = resetEpochSeconds * 1000L;
        long nowMs = System.currentTimeMillis();
        return Math.max(0, resetMs - nowMs + 1000L);
    }
}
