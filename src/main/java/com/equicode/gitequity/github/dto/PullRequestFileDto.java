package com.equicode.gitequity.github.dto;

// GET /repos/{owner}/{repo}/pulls/{pull_number}/files 응답 단건
public record PullRequestFileDto(
        String filename,
        String status,      // "added" | "modified" | "removed" | "renamed"
        int additions,
        int deletions,
        String patch        // unified diff (nullable — 바이너리 파일은 없음)
) {
    private static final java.util.Set<String> ANALYZABLE_EXTENSIONS = java.util.Set.of(
            "java", "py", "js", "ts", "go", "cpp", "c", "cs", "kt", "swift", "rb", "php"
    );

    public boolean isAnalyzable() {
        if (filename == null || "removed".equals(status)) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        return ANALYZABLE_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase());
    }
}
