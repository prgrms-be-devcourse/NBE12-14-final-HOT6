package com.back.nbe1214finalhot6.common.response;

public record ApiResponse<T>(boolean success, T data, String code, String message) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null);
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode) {
    return new ApiResponse<>(false, null, errorCode.name(), errorCode.getMessage());
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
    return new ApiResponse<>(false, null, errorCode.name(), message);
  }
}
