package com.back.nbe1214finalhot6.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

@Schema(description = "회원가입 요청")
public record SignupRequest(
    @Schema(description = "회원 이름. 한글(완성형, 공백 없음) 또는 영문(단어 사이 공백 한 칸)", example = "홍길동")
        @NotBlank
        @Size(max = 100) // 외국인의 경우 이름이 길어 가입이 어렵다는 리뷰를 고려한 길이
        @Pattern(
            // 한글만 또는 영문만. 숫자, 기호, 악센트 문자, 한글·영문 혼용은 거절. 빈 값은 @NotBlank 가 거절
            regexp = "^(?:[가-힣]++|[A-Za-z]++(?: [A-Za-z]++)*+)?$",
            message = "이름은 한글 또는 영문 중 하나로만 입력해야 하며, 띄어쓰기는 영문 이름에서 단어 사이 한 칸만 가능합니다.")
        String name,
    @Schema(description = "회원 이메일", example = "user@example.com") @NotBlank @Email @Size(max = 100)
        String email,
    @Schema(description = "8~15자이며 영문, 숫자, 특수문자(!@#%^&*)를 포함한 비밀번호", example = "Password1!")
        @NotBlank
        @Size(min = 8, max = 15)
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#%^&*])[A-Za-z\\d!@#%^&*]+$",
            message = "비밀번호는 영문, 숫자, 특수문자(!@#%^&*)를 각각 1자 이상 포함해야 합니다.")
        String password,
    @Schema(description = "전화번호. 하이픈 없이 숫자만", example = "01012345678")
        @NotBlank
        @Pattern(regexp = "^01[0-9]{8,9}$") // 하이픈불가
        String phone,
    @Schema(description = "로그인/결제에 사용되는 핀 비밀번호 6자리", example = "123456")
        @NotBlank
        @Pattern(regexp = "\\d{6}") // 6자리 숫자
        String pin) {

  // 이름 앞뒤 공백 제거, 이메일 앞뒤 공백 제거와 소문자변환
  public SignupRequest {
    name = name == null ? null : name.strip();
    email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }
}
