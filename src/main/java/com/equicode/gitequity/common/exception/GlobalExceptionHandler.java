package com.equicode.gitequity.common.exception;

import com.equicode.gitequity.common.response.ApiResponse;
import com.equicode.gitequity.github.GithubApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.getMessage()));
    }

    // @Valid 검증 실패 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    // GitHub API 오류 처리
    @ExceptionHandler(GithubApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleGithubApiException(GithubApiException e) {
        log.error("GitHub API error: {}", e.getMessage());
        HttpStatus status = e.getStatusCode() == 403 ? HttpStatus.FORBIDDEN
                : e.getStatusCode() == 404 ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(ApiResponse.fail(e.getMessage()));
    }

    // 그 외 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception: ", e);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
