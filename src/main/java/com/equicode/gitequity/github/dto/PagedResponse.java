package com.equicode.gitequity.github.dto;

import org.springframework.http.HttpHeaders;

import java.util.Collections;
import java.util.List;

// GitHub API 페이지네이션 응답 래퍼
// hasNext: Link 헤더에 rel="next"가 있으면 다음 페이지 존재
public record PagedResponse<T>(
        List<T> items,
        boolean hasNext,
        RateLimitInfo rateLimitInfo
) {
    public static <T> PagedResponse<T> from(List<T> body, HttpHeaders headers) {
        List<T> items = body != null ? body : Collections.emptyList();
        boolean hasNext = hasNextPage(headers);
        RateLimitInfo rateLimit = parseRateLimit(headers);
        return new PagedResponse<>(items, hasNext, rateLimit);
    }

    // Link: <...?page=2>; rel="next", <...?page=5>; rel="last"
    private static boolean hasNextPage(HttpHeaders headers) {
        String link = headers.getFirst("Link");
        return link != null && link.contains("rel=\"next\"");
    }

    private static RateLimitInfo parseRateLimit(HttpHeaders headers) {
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        String reset = headers.getFirst("X-RateLimit-Reset");
        int rem = remaining != null ? Integer.parseInt(remaining) : Integer.MAX_VALUE;
        long rst = reset != null ? Long.parseLong(reset) : 0L;
        return new RateLimitInfo(rem, rst);
    }
}
