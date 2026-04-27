package com.equicode.gitequity.contract.storage;

/** 계약서 PDF 저장소 추상화 — local / prod 프로파일로 구현체 분기 */
public interface ContractStorageService {
    /**
     * PDF 바이트 배열을 저장하고 접근 가능한 URL을 반환한다.
     * @param pdfBytes  iText7이 생성한 PDF 바이트
     * @param contractId 계약 ID (파일명 구성에 사용)
     */
    String uploadPdf(byte[] pdfBytes, Long contractId);
}
