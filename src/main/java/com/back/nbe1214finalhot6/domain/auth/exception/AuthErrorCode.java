package com.back.nbe1214finalhot6.domain.auth.exception;

import com.back.nbe1214finalhot6.common.response.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 인증·회원(AUTH) 에러 코드. */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
  // 회원가입
  DUPLICATE_EMAIL("AUTH-001", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
  DUPLICATE_PHONE("AUTH-002", HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다"),

  // 로그인. 이메일 없음과 비밀번호 불일치는 구분하지 않고 같은 응답을 준다(회원 존재 여부 노출 방지)
  LOGIN_FAILED(
      "AUTH-003",
      HttpStatus.UNAUTHORIZED,
      "이메일 또는 비밀번호가 올바르지 않습니다. 입력한 내용을 다시 확인해 주세요. 가입 전이라면 회원가입을 먼저 진행해 주세요."),
  // 비밀번호까지 맞은 탈퇴 계정에만 쓴다. 비밀번호를 아는 본인에게만 닿는 응답이라 안내해도 안전하다
  WITHDRAWN_USER("AUTH-004", HttpStatus.UNAUTHORIZED, "탈퇴한 계정입니다. 다시 이용하려면 관리자에게 문의해 주세요."),

  // PIN. 401 은 프론트가 "토큰 만료"로 처리하므로 쓰지 않는다. 불일치 메시지에는 남은 횟수를 직접 넣는다
  PIN_MISMATCH("AUTH-005", HttpStatus.BAD_REQUEST, "PIN이 일치하지 않습니다"),
  PIN_LOCKED("AUTH-006", HttpStatus.CONFLICT, "PIN이 5회 틀려 잠겼습니다. 잠금을 풀기 전에는 사용할 수 없습니다");

  private final String code;
  private final HttpStatus status;
  private final String message;
}
