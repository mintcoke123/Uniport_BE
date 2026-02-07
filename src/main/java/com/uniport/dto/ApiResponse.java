package com.uniport.dto;

import lombok.Getter;

/**
 * API 공통 응답 래퍼 클래스.
 *
 * @param <T> 응답 데이터(payload) 타입
 */
@Getter
public class ApiResponse<T> {

    private final String status;
    private final String message;
    private final T data;

    private ApiResponse(String status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    /**
     * 성공 응답을 생성합니다.
     *
     * @param data 응답 데이터
     * @return status "success", message null, data 설정된 ApiResponse
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("success", null, data);
    }

    /**
     * 성공 응답을 생성합니다. (메시지 포함)
     *
     * @param data    응답 데이터
     * @param message 응답 설명
     * @return status "success"인 ApiResponse
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>("success", message, data);
    }

    /**
     * 오류 응답을 생성합니다.
     *
     * @param message 에러 메시지
     * @return status "error", data null인 ApiResponse
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("error", message, null);
    }

    /**
     * 오류 응답을 생성합니다. (추가 데이터 포함)
     *
     * @param message 에러 메시지
     * @param data    추가 데이터(예: 검증 오류 상세)
     * @return status "error"인 ApiResponse
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>("error", message, data);
    }
}
