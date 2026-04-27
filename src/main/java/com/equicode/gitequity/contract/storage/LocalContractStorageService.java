package com.equicode.gitequity.contract.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 로컬 파일시스템에 PDF를 저장한다 (개발/테스트 환경).
 * 활성 프로파일: local (기본값 — prod 가 아닌 모든 환경)
 */
@Slf4j
@Service
@Profile("!prod")
public class LocalContractStorageService implements ContractStorageService {

    @Value("${app.storage.local-dir:${user.home}/gitequity-contracts}")
    private String storageDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public String uploadPdf(byte[] pdfBytes, Long contractId) {
        try {
            Path dir = Paths.get(storageDir);
            Files.createDirectories(dir);

            String filename = "contract-%d.pdf".formatted(contractId);
            Path dest = dir.resolve(filename);
            Files.write(dest, pdfBytes);

            String url = baseUrl + "/api/contracts/" + contractId + "/pdf";
            log.info("[Storage] saved locally: {} ({} bytes)", dest, pdfBytes.length);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("로컬 PDF 저장 실패", e);
        }
    }
}
