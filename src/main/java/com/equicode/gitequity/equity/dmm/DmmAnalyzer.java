package com.equicode.gitequity.equity.dmm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * scripts/dmm_analyzer.py(pydriller 기반)를 호출하여 main 브랜치 커밋(=squash merge PR)
 * 단위 DMM(Delta Maintainability Model)을 분석한다.
 *
 * 사전 설치:
 *   pip install -r scripts/requirements.txt
 */
@Slf4j
@Component
public class DmmAnalyzer {

    private static final String PYTHON_CMD = "python3";
    private static final long TIMEOUT_MINUTES = 10;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String scriptPath;

    private Boolean available = null;  // 최초 1회만 확인

    public DmmAnalyzer(@Value("${equity.dmm.script-path:scripts/dmm_analyzer.py}") String scriptPath) {
        this.scriptPath = scriptPath;
    }

    /**
     * @param repoPath 이미 clone되어 있는 로컬 저장소 경로
     * @return 커밋(=PR)별 DMM 분석 결과 목록
     * @throws Exception 파이썬 프로세스 실행/파싱 실패 시 (호출 측에서 DMM null로 두고 진행)
     */
    public List<CommitDmmResult> analyze(String repoPath) throws Exception {
        Process process = new ProcessBuilder(PYTHON_CMD, scriptPath, repoPath).start();

        StringBuilder stderr = new StringBuilder();
        Thread stderrReader = new Thread(() -> {
            try {
                readAll(process.getErrorStream(), stderr);
            } catch (Exception ignored) {}
        });
        stderrReader.start();

        StringBuilder stdout = new StringBuilder();
        readAll(process.getInputStream(), stdout);

        boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        stderrReader.join(TimeUnit.SECONDS.toMillis(5));

        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("dmm_analyzer.py timed out for repo=" + repoPath);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(
                    "dmm_analyzer.py failed (exit=%d): %s".formatted(process.exitValue(), stderr.toString().trim()));
        }

        return MAPPER.readValue(stdout.toString(), new TypeReference<List<CommitDmmResult>>() {});
    }

    public boolean isAvailable() {
        if (available != null) return available;
        try {
            Process p = new ProcessBuilder(PYTHON_CMD, "-c", "import pydriller")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
            available = (p.exitValue() == 0);
        } catch (Exception e) {
            available = false;
        }
        log.info("pydriller available: {}", available);
        return available;
    }

    private void readAll(InputStream in, StringBuilder out) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
    }
}
