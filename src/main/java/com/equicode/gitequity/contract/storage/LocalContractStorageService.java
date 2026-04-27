package com.equicode.gitequity.contract.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 비영구 환경(개발/Render 무료)용 PDF 저장: 파일을 저장하지 않고 null 반환.
 * downloadPdf 엔드포인트가 요청마다 온디맨드로 재생성한다.
 */
@Slf4j
@Service
@Profile("!prod")
public class LocalContractStorageService implements ContractStorageService {

    @Override
    public String uploadPdf(byte[] pdfBytes, Long contractId) {
        log.info("[Storage] skipping local save for contractId={} ({} bytes) — on-demand generation enabled", contractId, pdfBytes.length);
        return null;
    }
}
