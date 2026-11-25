package com.back.nbe1214finalhot6.domain.auth.config;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 회원·인증 설정값.
 *
 * <p>{@code auth.init} 은 서버를 시작할 때 만들 최초 운영자 계정이다. 비밀번호는 저장소에 두지 않고 환경 변수(`.env`)로만 받으며, 값이 비어 있으면 운영자 계정을 만들지
 * 않는다.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@DefaultValue Init init) {

  // 회원가입 요청(SignupRequest)과 같은 규칙이다. 한쪽을 바꾸면 이쪽도 바꾼다.
  private static final Pattern PASSWORD =
      Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#%^&*])[A-Za-z\\d!@#%^&*]{8,15}$");
  private static final Pattern PHONE = Pattern.compile("^01[0-9]{8,9}$");
  private static final Pattern PIN = Pattern.compile("^\\d{6}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

  /**
   * 최초 운영자 계정 값. 다섯 값이 모두 있어야 계정을 만든다.
   *
   * @param adminEmail 로그인 이메일
   * @param adminPassword 로그인 비밀번호(8~15자, 영문·숫자·특수문자 포함)
   * @param adminName 이름
   * @param adminPhone 전화번호(하이픈 없이 숫자만, 이용자와 겹치면 안 된다)
   * @param adminPin 간편 비밀번호(숫자 6자리)
   */
  public record Init(
      @DefaultValue("") String adminEmail,
      @DefaultValue("") String adminPassword,
      @DefaultValue("") String adminName,
      @DefaultValue("") String adminPhone,
      @DefaultValue("") String adminPin) {

    /** 다섯 값이 모두 채워져 있어 운영자 계정을 만들 수 있는지. 하나라도 비어 있으면 만들지 않는다. */
    public boolean enabled() {
      return !adminEmail.isBlank()
          && !adminPassword.isBlank()
          && !adminName.isBlank()
          && !adminPhone.isBlank()
          && !adminPin.isBlank();
    }

    /** 가입 때와 같은 방식으로 정리한 이메일(앞뒤 공백 제거, 소문자). */
    public String normalizedEmail() {
      return adminEmail.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * 값이 가입 규칙에 맞는지 확인한다. 어긋나면 서버 시작 때 바로 알 수 있도록 예외를 던진다. 비밀번호·PIN 값은 메시지에 넣지 않는다.
     */
    public void validate() {
      String email = normalizedEmail();
      if (!EMAIL.matcher(email).matches() || email.length() > 100) {
        throw new IllegalStateException("ADMIN_EMAIL 형식이 올바르지 않습니다");
      }
      if (!PASSWORD.matcher(adminPassword).matches()) {
        throw new IllegalStateException("ADMIN_PASSWORD 는 8~15자이며 영문, 숫자, 특수문자(!@#%^&*)를 포함해야 합니다");
      }
      if (adminName.strip().length() > 100) {
        throw new IllegalStateException("ADMIN_NAME 은 100자 이하여야 합니다");
      }
      if (!PHONE.matcher(adminPhone).matches()) {
        throw new IllegalStateException("ADMIN_PHONE 은 하이픈 없이 숫자만(예: 01012345678) 입력해야 합니다");
      }
      if (!PIN.matcher(adminPin).matches()) {
        throw new IllegalStateException("ADMIN_PIN 은 숫자 6자리여야 합니다");
      }
    }
  }
}
