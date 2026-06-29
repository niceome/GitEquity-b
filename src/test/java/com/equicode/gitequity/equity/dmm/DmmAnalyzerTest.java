package com.equicode.gitequity.equity.dmm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DmmAnalyzer 통합 테스트
 * pydriller가 설치되지 않은 환경에서는 전체 테스트를 skip한다.
 *
 * 설치: pip install -r scripts/requirements.txt
 */
class DmmAnalyzerTest {

    private final DmmAnalyzer analyzer = new DmmAnalyzer("scripts/dmm_analyzer.py");

    @BeforeEach
    void setUp() {
        assumeTrue(analyzer.isAvailable(),
                "pydriller not installed — skipping DMM tests. Install: pip install -r scripts/requirements.txt");
    }

    @Test
    @DisplayName("squash 커밋 메시지의 (#번호)에서 PR 번호를 추출하고 DMM 지표를 채워야 한다")
    void analyze_squashCommit_extractsPrNumberAndDmm(@TempDir Path repoDir) throws Exception {
        initRepoWithSquashCommit(repoDir, "src/Foo.java", """
                public class Foo {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """, "feat: add Foo (#42)");

        List<CommitDmmResult> results = analyzer.analyze(repoDir.toString());

        assertThat(results).hasSize(1);
        CommitDmmResult result = results.get(0);
        assertThat(result.prNumber()).isEqualTo(42);
        assertThat(result.message()).isEqualTo("feat: add Foo (#42)");
        assertThat(result.hash()).isNotBlank();
    }

    @Test
    @DisplayName("(#번호) 패턴이 없는 커밋은 prNumber가 null이어야 한다")
    void analyze_commitWithoutPrNumber_prNumberIsNull(@TempDir Path repoDir) throws Exception {
        initRepoWithSquashCommit(repoDir, "src/Bar.java", """
                public class Bar {
                    public int sub(int a, int b) {
                        return a - b;
                    }
                }
                """, "chore: misc cleanup");

        List<CommitDmmResult> results = analyzer.analyze(repoDir.toString());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).prNumber()).isNull();
    }

    // ── repo 초기화 헬퍼 ──────────────────────────────────────────────────────

    private void initRepoWithSquashCommit(Path repoDir, String relativeFile, String content, String message)
            throws Exception {
        runGit(repoDir, "init", "-b", "main");
        runGit(repoDir, "config", "user.email", "test@example.com");
        runGit(repoDir, "config", "user.name", "Test");

        Path file = repoDir.resolve(relativeFile);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);

        runGit(repoDir, "add", ".");
        runGit(repoDir, "commit", "-m", message);
    }

    private void runGit(Path dir, String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);

        Process process = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("git command failed: " + String.join(" ", command));
        }
    }
}
