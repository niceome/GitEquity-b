package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.github.dto.RateLimitInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Slf4j
final class CollectorUtils {

    // 남은 호출 횟수가 이 값 미만이면 경고 로그
    private static final int RATE_LIMIT_WARNING_THRESHOLD = 50;

    private CollectorUtils() {}

    static LocalDateTime parseIso(String iso) {
        return OffsetDateTime.parse(iso).toLocalDateTime();
    }

    // Rate limit 소진 시 리셋까지 블로킹 대기
    static void waitIfExhausted(RateLimitInfo info) {
        if (info == null || !info.isExhausted()) return;
        long waitMs = info.waitMillisUntilReset();
        log.warn("GitHub rate limit exhausted — waiting {}ms until reset", waitMs);
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 남은 호출 횟수가 임계치 미만이면 경고 로그만 남김 (대기하지 않음)
    static void warnIfRateLimitLow(RateLimitInfo info) {
        if (info == null) return;
        if (info.remaining() < RATE_LIMIT_WARNING_THRESHOLD) {
            log.warn("GitHub rate limit low — remaining={}, resetAt={}",
                    info.remaining(), info.resetEpochSeconds());
        }
    }
}
