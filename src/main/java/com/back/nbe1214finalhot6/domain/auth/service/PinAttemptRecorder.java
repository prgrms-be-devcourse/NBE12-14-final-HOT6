package com.back.nbe1214finalhot6.domain.auth.service;

import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PIN 시도 결과(실패 횟수, 잠금)를 기록한다.
 *
 * <p>호출한 쪽이 이미 트랜잭션 안에 있어도 <b>항상 새 트랜잭션</b>(REQUIRES_NEW)으로 저장한다. PIN 이 틀려 예외가 나면 호출한 쪽(예: 결제)이 롤백되는데, 그때
 * 올린 실패 횟수까지 같이 사라지면 PIN 을 무한히 시도할 수 있기 때문이다. 같은 클래스의 메서드를 안에서 부르면 이 설정이 적용되지 않으므로 별도 빈으로 분리했다.
 */
@Component
@RequiredArgsConstructor
class PinAttemptRecorder {

  private final UserRepository userRepository;
  private final Clock clock;

  /**
   * 실패를 기록하고 기록 후의 상태를 돌려준다. 이미 잠겨 있었으면(반영된 행 0) 잠금으로 돌려준다.
   *
   * @param maxFailures 이 횟수에 닿으면 잠근다
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Result recordFailure(Long userId, int maxFailures) {
    int updated = userRepository.recordPinFailure(userId, maxFailures, LocalDateTime.now(clock));
    if (updated == 0) {
      return Result.alreadyLocked();
    }
    return userRepository
        .findById(userId)
        .map(user -> new Result(user.getPinFailCount(), user.getPinLockedAt() != null))
        .orElseGet(Result::alreadyLocked);
  }

  /** 맞는 PIN 이면 실패 횟수를 0으로 되돌린다(새 트랜잭션). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reset(Long userId) {
    userRepository.resetPinFailCount(userId);
  }

  /** 실패를 기록한 뒤의 실패 횟수와 잠금 여부. */
  record Result(int failCount, boolean locked) {
    static Result alreadyLocked() {
      return new Result(0, true);
    }
  }
}
