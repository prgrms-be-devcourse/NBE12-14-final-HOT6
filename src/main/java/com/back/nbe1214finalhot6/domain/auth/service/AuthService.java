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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

  // 이용자 가입에서 이메일·전화번호가 겹치면 안 되는 계정 종류. 점주 계정과는 같은 값을 써도 된다.
  // 운영자의 이메일·전화번호는 로그인 대상이 섞이지 않게 계속 막는다
  private static final Set<UserRole> USER_SIGNUP_BLOCKING_ROLES =
      Collections.unmodifiableSet(EnumSet.of(UserRole.USER, UserRole.ADMIN));

  // 로그인할 때 확인하는 순서. 활동 중인 계정을 먼저, 그다음 USER, OWNER, ADMIN
  private static final Comparator<User> LOGIN_PRIORITY =
      Comparator.comparing((User user) -> user.getStatus() != UserStatus.ACTIVE)
          .thenComparing(User::getRole);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final WalletOpeningService walletOpeningService;
  private final JwtProvider jwtProvider;

  @Transactional
  public SignupResponse signup(SignupRequest request) {
    // 이메일 중복이면 예외
    if (userRepository.existsByRoleInAndEmail(USER_SIGNUP_BLOCKING_ROLES, request.email())) {
      throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
    }
    // 전화번호 중복이면 예외
    if (userRepository.existsByRoleInAndPhone(USER_SIGNUP_BLOCKING_ROLES, request.phone())) {
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
    User user =
        findByPassword(userRepository.findAllByEmail(request.email()), request.password());

    // 이메일 없음과 비밀번호 불일치는 같은 에러로 거절한다(어느 쪽인지 알려 주지 않는다)
    if (user == null) {
      throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
    }
    // 비밀번호까지 맞았는데 탈퇴한 계정이면 안내한다. 비밀번호를 모르는 사람은 이 응답에 닿지 못한다
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(AuthErrorCode.WITHDRAWN_USER);
    }

    String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
    return LoginResponse.bearer(accessToken, jwtProvider.accessTokenExpiresInSeconds());
  }

  // 같은 이메일을 이용자 계정과 점주 계정이 함께 쓸 수 있어, 비밀번호가 맞는 계정을 찾는다. 한 계정만 고르면 같은 이메일로
  // 가입한 다른 계정 때문에 로그인이 막힐 수 있다. 비밀번호가 둘 다 맞으면 LOGIN_PRIORITY 의 앞선 계정이다
  private User findByPassword(List<User> accounts, String rawPassword) {
    // 회원이 없어도 비밀번호 비교는 한다(응답 시간 차이 방지)
    if (accounts.isEmpty()) {
      passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
      return null;
    }
    return accounts.stream()
        .sorted(LOGIN_PRIORITY)
        .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()))
        .findFirst()
        .orElse(null);
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
