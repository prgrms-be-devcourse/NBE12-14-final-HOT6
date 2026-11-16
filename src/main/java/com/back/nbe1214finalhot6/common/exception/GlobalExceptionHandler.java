package com.back.nbe1214finalhot6.common.exception;

import com.back.nbe1214finalhot6.common.response.ApiResponse;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    return response(e.getErrorCode());
  }

  // 필수 헤더 누락. Idempotency-Key 가 없으면 전용 코드로 응답한다.
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
    if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
      return response(ErrorCode.IDEMPOTENCY_KEY_INVALID);
    }
    return response(ErrorCode.INVALID_REQUEST);
  }

  // DTO 검증 실패 (예: @NotBlank 위반)
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotValid(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining(", "));
    return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
        .body(ApiResponse.fail(ErrorCode.INVALID_REQUEST, message));
  }

  // 요청 본문을 DTO 로 변환할 수 없는 경우
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
    return response(ErrorCode.INVALID_REQUEST);
  }

  // 파라미터 타입 오류 (예: 숫자 자리에 문자)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException e) {
    return response(ErrorCode.INVALID_REQUEST);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
    return response(ErrorCode.NOT_FOUND);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException e) {
    return response(ErrorCode.METHOD_NOT_ALLOWED);
  }

  // 낙관적 락(@Version) 충돌
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
      OptimisticLockingFailureException e) {
    return response(ErrorCode.CONCURRENT_UPDATE);
  }

  // 서비스에서 잡지 못한 DB 제약 위반의 최후 방어선.
  // 원문 메시지에는 제약 이름·테이블 구조가 들어 있어 응답에 담지 않는다.
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
    log.warn("데이터 제약 위반", e);
    return response(ErrorCode.DATA_CONFLICT);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("처리되지 않은 예외 발생", e);
    return response(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
  }
}
