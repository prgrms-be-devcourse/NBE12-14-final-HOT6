package com.back.nbe1214finalhot6.common.response;

public record ApiResponse<T>(boolean success, String code, String message, T data) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, null, null, data);
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode) {
    return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
    return new ApiResponse<>(false, errorCode.getCode(), message, null);
  }
}
