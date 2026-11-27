package com.back.nbe1214finalhot6.domain.auth.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
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
  private final Clock clock;

  /**
   * PIN 이 맞는지 확인한다. 맞으면 아무 일도 없고(실패 횟수는 0으로), 아니면 예외를 던진다.
   *
   * <ul>
   *   <li>없는 회원: COM-009(404)
   *   <li>탈퇴한 회원: AUTH-004(401)
   *   <li>이미 잠긴 회원: AUTH-006(409). 맞는 PIN 이어도 거절한다. 잠긴 지 10분이 지나면 자동으로 풀린다(임시 방식, MVP 이후 이메일 인증 해제로 대체)
   *   <li>틀림: AUTH-005(400), 메시지에 남은 횟수. 5번째로 틀려 잠기면 AUTH-006(409)
   * </ul>
   */
  public void verify(Long userId, String pin) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    // 인증 필터를 통과한 뒤에 앞 요청의 탈퇴가 끝날 수 있다. 필터와 같은 에러로 알려야 화면이 탈퇴 완료로 안내한다
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(AuthErrorCode.WITHDRAWN_USER);
    }
    LocalDateTime now = LocalDateTime.now(clock);
    if (user.isPinLocked(now)) {
      throw new BusinessException(AuthErrorCode.PIN_LOCKED);
    }
    if (user.getPinLockedAt() != null) {
      // 잠긴 지 User.PIN_LOCK_DURATION(10분)이 지났다. 잠금과 실패 횟수를 초기화하고 처음부터 다시 시작한다.
      // (임시 방식: MVP 이후에는 이메일 인증으로 해제(U7)로 바꾼다. User.PIN_LOCK_DURATION 참고)
      recorder.unlockIfExpired(userId, now.minus(User.PIN_LOCK_DURATION));
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
