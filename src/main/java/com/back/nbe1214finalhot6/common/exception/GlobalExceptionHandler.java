package com.back.nbe1214finalhot6.common.exception;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.fail(errorCode, e.getMessage()));
  }

  // 필수 헤더 누락. Idempotency-Key 이면 COM-002, 그 외 헤더는 COM-001.
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
    if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
      return response(CommonErrorCode.IDEMPOTENCY_KEY_MISSING);
    }
    return response(CommonErrorCode.INVALID_REQUEST);
  }

  // DTO 검증 실패 (예: @NotBlank 위반)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotValid(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining(", "));
    return ResponseEntity.status(CommonErrorCode.INVALID_REQUEST.getStatus())
        .body(ApiResponse.fail(CommonErrorCode.INVALID_REQUEST, message));
  }

  // 요청 본문을 DTO 로 변환할 수 없는 경우
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
    return response(CommonErrorCode.INVALID_REQUEST);
  }

  // 필수 쿼리 파라미터 누락
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
      MissingServletRequestParameterException e) {
    return response(CommonErrorCode.INVALID_REQUEST);
  }

  // @RequestParam, @PathVariable 등 메서드 파라미터 검증 실패
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
      HandlerMethodValidationException e) {
    return response(CommonErrorCode.INVALID_REQUEST);
  }

  // 지원하지 않는 Content-Type
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException e) {
    return response(CommonErrorCode.INVALID_REQUEST);
  }

  // 파라미터 타입 오류 (예: 숫자 자리에 문자)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException e) {
    return response(CommonErrorCode.INVALID_REQUEST);
  }

  // 지원하지 않는 HTTP 메서드. 명세에 별도 코드가 없어 COM-001 로 처리한다.
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    return response(CommonErrorCode.INVALID_REQUEST);
  }

  // 존재하지 않는 URL
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
    return response(CommonErrorCode.RESOURCE_NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("처리되지 않은 예외 발생", e);
    return response(CommonErrorCode.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
  }
}
