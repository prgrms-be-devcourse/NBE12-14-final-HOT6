package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyLimitRepository;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용자 월 적립 한도 사용량 관리. 캐시백 도메인 내부 전용이며, 결제 도메인은 {@code CashbackService} 만 호출한다.
 *
 * <p>한도 금액은 설정값이라 이 서비스가 읽지 않고 호출한 쪽이 넘긴다. 월은 결제를 최초로 접수한 시각의 한국 시간 월이다.
 */
@Service
@RequiredArgsConstructor
public class CashbackMonthlyLimitService {

  private final CashbackMonthlyLimitRepository limitRepository;

  /**
   * 적립액만큼 이번 달 한도를 사용한다. 사용량 + 적립액이 한도 이하일 때만 늘리고, 넘으면 아무것도 바꾸지 않고 {@code false} 를 돌려준다. 한도를
   * 넘는 것은 정상적인 결과라 예외가 아니다(결제는 그대로 진행된다). 호출한 쪽의 트랜잭션에 참여한다.
   *
   * <p>그 달의 행이 없으면 먼저 만든다. 이미 행이 있는 흔한 경우에는 UPDATE 한 번으로 끝나고, 같은 이용자의 같은 달 행을 동시에 처음 만들어도 한쪽이
   * 실패하지 않는다. 영속성 컨텍스트를 비우지 않으므로 같은 트랜잭션에서 읽어 둔 엔티티가 분리되지 않는다.
   *
   * @param limit 지금 적용되는 월 한도(원)
   * @throws BusinessException 금액이 0 이하일 때(CSH-007)
   */
  @Transactional
  public boolean consume(Long walletId, YearMonth month, long amount, long limit) {
    validateAmount(amount);
    var firstDay = month.atDay(1);
    if (limitRepository.consume(walletId, firstDay, amount, limit) > 0) {
      return true;
    }
    // 행이 없어서 실패했을 수 있다. 만든 뒤 한 번 더 시도하고, 그래도 안 되면 한도 초과다
    limitRepository.insertIfAbsent(walletId, firstDay);
    return limitRepository.consume(walletId, firstDay, amount, limit) > 0;
  }

  /**
   * 적립액만큼, 남은 한도가 모자라면 남은 한도만큼 이번 달 한도를 사용하고 실제로 사용한 금액을 돌려준다. 남은 한도가 없으면 아무것도 바꾸지 않고 0 을
   * 돌려준다(예외가 아니다). 호출한 쪽의 트랜잭션에 참여한다.
   *
   * <p>그 행을 잠그고 읽은 사용량으로 확보량을 정한 뒤 늘린다. 같은 이용자의 같은 달 결제는 이 잠금에서 줄을 서므로 확보량의 합이 한도를 넘지 않는다.
   * 잠금은 호출한 쪽 트랜잭션이 끝날 때 풀린다.
   *
   * @param limit 지금 적용되는 월 한도(원)
   * @return 실제로 사용한 금액(0 이상, {@code amount} 이하)
   * @throws BusinessException 금액이 0 이하일 때(CSH-007)
   */
  @Transactional
  public long consumeUpTo(Long walletId, YearMonth month, long amount, long limit) {
    validateAmount(amount);
    var firstDay = month.atDay(1);
    long used =
        limitRepository
            .findUsedAmountForUpdate(walletId, firstDay)
            .orElseGet(
                () -> {
                  limitRepository.insertIfAbsent(walletId, firstDay);
                  return limitRepository.findUsedAmountForUpdate(walletId, firstDay).orElse(0L);
                });
    long granted = Math.min(amount, Math.max(0L, limit - used));
    if (granted > 0) {
      limitRepository.consume(walletId, firstDay, granted, limit);
    }
    return granted;
  }

  /**
   * 사용했던 한도를 되돌린다. 결제 취소나 예산 확보 실패 때 호출하며, 호출한 쪽의 트랜잭션에 참여한다. 취소는 결제한 달의 행에서 되돌리므로 호출한 쪽이
   * 그 달을 넘긴다.
   *
   * @throws BusinessException 금액이 0 이하이거나 사용량보다 많이 되돌리려 할 때(CSH-007). 예외가 나면 호출한 쪽 트랜잭션이 롤백된다
   */
  @Transactional
  public void release(Long walletId, YearMonth month, long amount) {
    validateAmount(amount);
    if (limitRepository.release(walletId, month.atDay(1), amount) == 0) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_BUDGET, "사용한 한도보다 많이 되돌릴 수 없습니다");
    }
  }

  /** 이번 달에 사용한 한도(적립 예정 포함). 행이 없으면 0 이다. */
  @Transactional(readOnly = true)
  public long usedAmount(Long walletId, YearMonth month) {
    return limitRepository.findUsedAmount(walletId, month.atDay(1)).orElse(0L);
  }

  private static void validateAmount(long amount) {
    if (amount <= 0) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "금액은 0보다 커야 합니다");
    }
  }
}
