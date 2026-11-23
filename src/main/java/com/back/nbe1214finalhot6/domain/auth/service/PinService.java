package com.back.nbe1214finalhot6.domain.auth.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 간편 비밀번호(PIN) 검증. 5회 틀리면 잠긴다.
 *
 * <p>이 메서드는 트랜잭션을 열지 않는다. BCrypt 비교가 느려서 그동안 DB 연결을 잡고 있지 않으려는 것이고, 실패 횟수·잠금은 {@link PinAttemptRecorder}가 새 트랜잭션으로
 * 저장하므로 호출한 쪽(결제 등)이 예외로 롤백돼도 횟수는 남는다.
 */
@Service
@RequiredArgsConstructor
public class PinService {

  /** 이 횟수만큼 틀리면 잠근다. */
  static final int MAX_FAILURES = 5;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PinAttemptRecorder recorder;

  /**
   * PIN 이 맞는지 확인한다. 맞으면 아무 일도 없고(실패 횟수는 0으로), 아니면 예외를 던진다.
   *
   * <ul>
   *   <li>없는 회원이거나 탈퇴한 회원: COM-009(404)
   *   <li>이미 잠긴 회원: AUTH-006(409). 맞는 PIN 이어도 거절한다
   *   <li>틀림: AUTH-005(400), 메시지에 남은 횟수. 5번째로 틀려 잠기면 AUTH-006(409)
   * </ul>
   */
  public void verify(Long userId, String pin) {
    User user =
        userRepository
            .findById(userId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (user.getPinLockedAt() != null) {
      throw new BusinessException(AuthErrorCode.PIN_LOCKED);
    }

    if (passwordEncoder.matches(pin, user.getPinHash())) {
      recorder.reset(userId);
      return;
    }

    PinAttemptRecorder.Result result = recorder.recordFailure(userId, MAX_FAILURES);
    if (result.locked()) {
      throw new BusinessException(AuthErrorCode.PIN_LOCKED);
    }
    int remaining = MAX_FAILURES - result.failCount();
    throw new BusinessException(
        AuthErrorCode.PIN_MISMATCH, "PIN이 일치하지 않습니다 (잔여 " + remaining + "회)");
  }
}
