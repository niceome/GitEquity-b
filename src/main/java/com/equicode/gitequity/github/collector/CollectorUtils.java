package com.equicode.gitequity.github.collector;

import com.equicode.gitequity.github.dto.RateLimitInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Slf4j
final class CollectorUtils {

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
}
