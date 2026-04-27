package com.equicode.gitequity.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 프로젝트
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    PROJECT_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트 멤버를 찾을 수 없습니다."),
    PROJECT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 연결된 레포지토리입니다."),

    // 계약
    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "계약을 찾을 수 없습니다."),
    CONTRACT_NOT_SENDABLE(HttpStatus.BAD_REQUEST, "DRAFT 상태의 계약만 발송할 수 있습니다."),
    CONTRACT_NOT_SIGNABLE(HttpStatus.BAD_REQUEST, "PENDING 상태의 계약만 서명할 수 있습니다."),
    CONTRACT_NOT_DELETABLE(HttpStatus.BAD_REQUEST, "완료된 계약은 삭제할 수 없습니다."),
    SIGNATURE_NOT_FOUND(HttpStatus.NOT_FOUND, "서명 대상자가 아닙니다."),
    ALREADY_SIGNED(HttpStatus.CONFLICT, "이미 서명한 계약입니다."),

    // GitHub
    GITHUB_API_ERROR(HttpStatus.BAD_GATEWAY, "GitHub API 오류가 발생했습니다."),
    REPO_NOT_FOUND(HttpStatus.BAD_REQUEST, "GitHub 레포지토리를 찾을 수 없습니다. owner/repo 이름을 확인하세요.");

    private final HttpStatus status;
    private final String message;
}
