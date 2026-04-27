package com.equicode.gitequity.equity.ccn;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lizard CLI를 사용한 순환 복잡도(CCN) 분석기
 *
 * 사전 설치:
 *   pip install lizard
 *   또는 Docker: docker run --rm -v $(pwd):/src terryyin/lizard /src
 *
 * lizard --csv 출력 포맷 (함수 단위):
 *   NLOC,CCN,token,PARAM,length,location,filename,line_no,fun_name
 */
@Slf4j
@Service
public class CcnAnalyzer {

    private static final String LIZARD_CMD = "lizard";
    private static Boolean lizardAvailable = null;  // 최초 1회만 확인

    // ── 공개 API ──────────────────────────────────────────────────────────────

    /**
     * 코드 문자열을 분석하여 CcnResult를 반환한다.
     * lizard가 없으면 CcnResult.unknown() 반환 (ccnScore=1.0)
     */
    public CcnResult analyze(String code, String filename) {
        if (!isLizardAvailable()) {
            log.debug("lizard unavailable — returning default ccnScore for {}", filename);
            return CcnResult.unknown(filename);
        }

        Path tempFile = null;
        try {
            String suffix = extensionOf(filename);
            tempFile = Files.createTempFile("gitequity-ccn-", suffix);
            Files.writeString(tempFile, code);
            return runLizard(tempFile, filename);
        } catch (Exception e) {
            log.error("CCN analysis failed for {}: {}", filename, e.getMessage());
            return CcnResult.unknown(filename);
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    public boolean isLizardAvailable() {
        if (lizardAvailable != null) return lizardAvailable;
        try {
            Process p = new ProcessBuilder(LIZARD_CMD, "--version")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
            lizardAvailable = (p.exitValue() == 0);
        } catch (Exception e) {
            lizardAvailable = false;
        }
        log.info("lizard available: {}", lizardAvailable);
        return lizardAvailable;
    }

    // ── 내부 구현 ─────────────────────────────────────────────────────────────

    private CcnResult runLizard(Path file, String originalFilename) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(LIZARD_CMD, "--csv", file.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<FunctionEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseCsvLine(line).ifPresent(entries::add);
            }
        }
        process.waitFor();

        if (entries.isEmpty()) {
            return CcnResult.unknown(originalFilename);  // 함수 없으면 기본값(ccnScore=1.0)
        }

        double avgCcn = entries.stream()
                .mapToInt(FunctionEntry::ccn)
                .average()
                .orElse(1.0);

        return new CcnResult(originalFilename, avgCcn, entries.size());
    }

    // CSV: NLOC,CCN,token,PARAM,length,location,filename,line_no,fun_name
    private Optional<FunctionEntry> parseCsvLine(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length < 2) return Optional.empty();
        try {
            int ccn = Integer.parseInt(parts[1].trim());
            return Optional.of(new FunctionEntry(ccn));
        } catch (NumberFormatException e) {
            return Optional.empty();  // 헤더 행 등 숫자가 아닌 줄 무시
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".tmp";
    }

    private record FunctionEntry(int ccn) {}
}
