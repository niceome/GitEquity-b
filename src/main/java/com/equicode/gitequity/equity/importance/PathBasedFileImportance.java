package com.equicode.gitequity.equity.importance;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 파일 경로 패턴으로 FileImportance를 분류한다.
 * 우선순위: CRITICAL → HIGH → LOW → MEDIUM(기본)
 */
@Component
public class PathBasedFileImportance {

    // ── CRITICAL 패턴: 보안·인증·결제·암호화 ──────────────────────────────────
    private static final List<String> CRITICAL_PATTERNS = List.of(
            "auth", "security", "payment", "billing", "crypto",
            "oauth", "jwt", "encrypt", "decrypt", "token", "credential"
    );

    // ── HIGH 패턴: 비즈니스 로직 핵심 레이어 ────────────────────────────────────
    private static final List<String> HIGH_PATTERNS = List.of(
            "service", "domain", "controller", "repository",
            "api", "handler", "usecase", "core", "engine"
    );

    // ── LOW 패턴: 테스트·문서·빌드 산출물 ────────────────────────────────────────
    private static final List<String> LOW_PATTERNS = List.of(
            "test", "spec", "__test__", "__mock__",
            "readme", "docs", "document",
            "build/", "dist/", "target/", "generated/", ".generated",
            "migration", "fixture", "stub", "mock"
    );

    public FileImportance classify(String filepath) {
        if (filepath == null || filepath.isBlank()) return FileImportance.MEDIUM;

        String lower = filepath.toLowerCase().replace('\\', '/');

        // LOW 우선 체크: 테스트/문서/빌드 파일은 경로에 service, api 등이 포함돼도 LOW
        if (isTestOrDoc(lower))                   return FileImportance.LOW;
        if (matchesAny(lower, CRITICAL_PATTERNS)) return FileImportance.CRITICAL;
        if (matchesAny(lower, HIGH_PATTERNS))     return FileImportance.HIGH;
        return FileImportance.MEDIUM;
    }

    private boolean isTestOrDoc(String lower) {
        return lower.contains("/test/") || lower.startsWith("test/")
            || lower.contains("/spec/") || lower.startsWith("spec/")
            || lower.contains("__test__") || lower.contains("__mock__")
            || lower.endsWith("test.java") || lower.endsWith("tests.java")
            || lower.endsWith("spec.java") || lower.endsWith(".test.ts")
            || lower.endsWith(".spec.ts") || lower.endsWith(".test.js")
            || lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".rst")
            || lower.contains("/docs/") || lower.startsWith("docs/")
            || lower.contains("/build/") || lower.contains("/dist/")
            || lower.contains("/target/") || lower.contains("/generated/")
            || lower.contains("fixture") || lower.contains("/stub/") || lower.contains("/mock/");
    }

    public double scoreOf(String filepath) {
        return classify(filepath).getScore();
    }

    private boolean matchesAny(String lower, List<String> patterns) {
        return patterns.stream().anyMatch(lower::contains);
    }
}
