package com.equicode.gitequity.common.response;

// 모든 API 응답에 사용하는 공통 포맷
// success: 성공 여부, message: 결과 메시지, data: 실제 응답 데이터
public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "성공", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
