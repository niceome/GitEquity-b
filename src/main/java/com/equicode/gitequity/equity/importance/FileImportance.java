package com.equicode.gitequity.equity.importance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파일 경로 기반 중요도 등급
 * score: rawScore 공식의 fileImportance 승수로 직접 사용
 *
 *  CRITICAL(2.0) — 보안·인증·결제 등 핵심 비즈니스 로직
 *  HIGH    (1.5) — 서비스·도메인·컨트롤러·저장소 레이어
 *  MEDIUM  (1.0) — 설정·DTO·유틸 (기본값)
 *  LOW     (0.5) — 테스트·문서·빌드 산출물
 */
@Getter
@RequiredArgsConstructor
public enum FileImportance {
    CRITICAL(2.0),
    HIGH(1.5),
    MEDIUM(1.0),
    LOW(0.5);

    private final double score;
}
