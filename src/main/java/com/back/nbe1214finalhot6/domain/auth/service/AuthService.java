package com.back.nbe1214finalhot6.domain.auth.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.LoginResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletOpeningService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  // 없는 이메일로 로그인해도 비밀번호 비교를 한 번 하도록 쓰는 BCrypt 해시(평문은 아무 값이나 해당 없음).
  // 비교 시간이 같아야 응답 속도로 가입 여부를 알아내지 못한다.
  private static final String DUMMY_PASSWORD_HASH =
      "$2y$10$mPWVWlLpLgssl1EggKaYk.DpDfaw2noVJOG01Bjc6s/v4RKIUtb.C";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final WalletOpeningService walletOpeningService;
  private final JwtProvider jwtProvider;

  @Transactional
  public SignupResponse signup(SignupRequest request) {
    // 이메일 중복이면 예외
    if (userRepository.existsByEmail(request.email())) {
      throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
    }
    // 전화번호 중복이면 예외
    if (userRepository.existsByPhone(request.phone())) {
      throw new BusinessException(AuthErrorCode.DUPLICATE_PHONE);
    }
    // 암호화
    String passwordHash = passwordEncoder.encode(request.password());
    String pinHash = passwordEncoder.encode(request.pin());

    User user =
        User.create(
            request.name(), request.email(), request.phone(), passwordHash, pinHash, UserRole.USER);

    User saved = saveOrThrowDuplicate(user);

    // 같은 트랜잭션에서 모의계좌·가입 보너스 원장·지갑 생성. 하나라도 실패하면 회원 저장까지 롤백됨
    walletOpeningService.open(saved.getId());

    return SignupResponse.from(saved);
  }

  // 트랜잭션을 두지 않는다. 조회한 뒤에 하는 BCrypt 비교(느림) 동안 DB 연결을 잡고 있지 않기 위해서다.
  public LoginResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.email()).orElse(null);

    // 회원이 없어도 비밀번호 비교는 한다(응답 시간 차이 방지)
    String hash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
    boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

    // 이메일 없음, 비밀번호 불일치, 탈퇴 회원은 같은 에러로 거절한다
    if (user == null || !passwordMatches || user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
    }

    String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
    return LoginResponse.bearer(accessToken, jwtProvider.accessTokenExpiresInSeconds());
  }

  // 동시 가입으로 위의 중복 확인을 함께 통과한 요청은 DB unique 제약이 막는다. 이를 중복 에러로 바꾼다.
  // 예외가 나면 이 트랜잭션은 롤백되므로 여기서 다시 조회하지 않고 제약 이름으로 구분한다.
  private User saveOrThrowDuplicate(User user) {
    try {
      // 저장 즉시 flush 해서 unique 위반이 이 try 안에서 나도록 한다(ID 생성 방식에 기대지 않음)
      return userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      String constraint = constraintName(e);
      // 위의 사전 확인과 같은 에러를 던지는 것은 의도한 중복이다. 사전 확인은 순차 요청을, 여기는 동시 요청을 거른다.
      if (constraint.contains("email")) {
        throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
      }
      if (constraint.contains("phone")) {
        throw new BusinessException(AuthErrorCode.DUPLICATE_PHONE);
      }
      throw e; // 이메일·전화번호 제약이 아니면 중복이라고 단정하지 않고 그대로
    }
  }

  private String constraintName(DataIntegrityViolationException e) {
    if (e.getCause() instanceof ConstraintViolationException cause
        && cause.getConstraintName() != null) {
      return cause.getConstraintName().toLowerCase(Locale.ROOT);
    }
    return "";
  }
}
