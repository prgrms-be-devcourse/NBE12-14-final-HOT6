package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletService;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 도메인이 쓰는 캐시백 창구. 결제 트랜잭션 안에서 호출하며, 결제가 롤백되면 예산·한도 확보와 적립 건도 함께 롤백된다.
 *
 * <p>메서드 시그니처와 반환 타입은 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CashbackService {

  private final CashbackEarningRepository earningRepository;
  private final CashbackPolicyService policyService;
  private final CashbackMonthlyLimitService limitService;
  private final CashbackBudgetService budgetService;
  private final OperatorSettingService settingService;
  private final WalletService walletService;
  private final CashbackProperties properties;
  private final Clock clock;

  /**
   * 적립액을 계산하고 그만큼의 이용자 월 한도와 예산을 확보한다. 호출한 쪽(결제)의 트랜잭션에 참여한다.
   *
   * <p>적립은 전액 또는 0원이다. 예산이 부족하거나 한도를 넘으면 예외 없이 0원과 사유를 돌려주며, 결제는 그대로 진행한다. 제외된 결제도 적립 건을
   * 남긴다. 같은 결제 ID 로 다시 호출하면 새로 확보하지 않고 처음 결과를 돌려준다.
   *
   * <p>제외 사유는 먼저 걸리는 하나만 쓴다: 적립 대상 아님, 데브머니 사용분 없음, 적립액 1원 미만, 월 한도 초과, 예산 소진 순이다. 요율은 최초
   * 접수 시각 기준이고 적립액은 소수점 이하를 버린다. 월은 접수 시각의 월이다.
   *
   * <p>예산은 결제 트랜잭션에서 경합이 가장 큰 자원이라 마지막에 확보한다. 예산 확보가 실패하면 먼저 차감한 월 한도를 되돌린다.
   */
  @Transactional
  public CashbackReserveResponse reserve(CashbackReserveRequest command) {
    long limit = settingService.getLong(SettingKey.CASHBACK_USER_MONTHLY_LIMIT);

    Optional<CashbackEarning> existing = earningRepository.findByPaymentId(command.paymentId());
    if (existing.isPresent()) {
      return replay(existing.get(), limit);
    }

    LocalDateTime requestedAt = command.requestedAt();
    YearMonth month = YearMonth.from(requestedAt);
    LocalDateTime now = LocalDateTime.now(clock);

    if (!command.cashbackEligible()) {
      return exclude(command, CashbackExclusionReason.NOT_ELIGIBLE, month, limit, now);
    }
    if (command.walletUsedAmount() <= 0) {
      return exclude(command, CashbackExclusionReason.NO_WALLET_USAGE, month, limit, now);
    }

    // 달 중 인하 구간이 있으면 그 요율, 없으면 그 달 월 예산의 기본 요율이다. 둘 다 없으면 적립할 예산이 없는 달이다
    Optional<PolicyResponse> segment = policyService.findEffective(requestedAt);
    Optional<BigDecimal> rate =
        segment.map(PolicyResponse::rate).or(() -> policyService.effectiveRate(requestedAt));
    if (rate.isEmpty()) {
      return exclude(command, CashbackExclusionReason.BUDGET_EXHAUSTED, month, limit, now);
    }

    long amount =
        BigDecimal.valueOf(command.walletUsedAmount())
            .multiply(rate.get())
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
    if (amount <= 0) {
      return exclude(command, CashbackExclusionReason.AMOUNT_TOO_SMALL, month, limit, now);
    }

    if (!limitService.consume(command.walletId(), month, amount, limit)) {
      return exclude(command, CashbackExclusionReason.MONTHLY_LIMIT_EXCEEDED, month, limit, now);
    }
    if (!budgetService
        .reserveBudget(month.getYear(), month.getMonthValue(), amount)
        .isReserved()) {
      limitService.release(command.walletId(), month, amount);
      return exclude(command, CashbackExclusionReason.BUDGET_EXHAUSTED, month, limit, now);
    }

    // 결제 다음날 00:00 + 지급 지연 일수
    LocalDateTime payableAt =
        requestedAt.toLocalDate().plusDays(1L + properties.payoutDelayDays()).atStartOfDay();
    CashbackEarning earning =
        earningRepository.save(
            CashbackEarning.pending(
                command.paymentId(),
                command.walletId(),
                command.walletUsedAmount(),
                requestedAt,
                rate.get(),
                segment.map(PolicyResponse::policyId).orElse(null),
                amount,
                payableAt,
                month.atDay(1),
                now));
    return new CashbackReserveResponse(
        amount,
        earning.getAppliedRate(),
        earning.getAppliedPolicyId(),
        payableAt,
        CashbackExclusionReason.NONE,
        remaining(command.walletId(), month, limit));
  }

  /**
   * 결제 취소에 따라 적립을 되돌린다. 호출한 쪽(결제 취소)의 트랜잭션에 참여하므로 취소가 롤백되면 복원도 함께 롤백된다.
   *
   * <ul>
   *   <li>지급 전(적립 예정): 취소로 바꾸고, 확보했던 이용자 월 한도와 예산(연 -> 월)을 돌려준다.
   *   <li>지급 후(지급 완료): 지갑 캐시백 잔액에서 회수하고(잔액이 모자라면 음수), 회수로 바꾸고, 월 한도를 돌려준다. 이미 지급으로 옮긴 예산은 되돌리지
   *       않는다.
   * </ul>
   *
   * <p>같은 결제를 여러 번 취소해도 한 번만 처리하고, 적립 건이 없거나 제외 건이거나 이미 취소·회수된 경우에는 아무것도 하지 않는다. 한도는 결제한 달에서
   * 되돌리며 이번 달 한도는 늘리지 않는다. 상태 전환이 조건부 UPDATE 한 문장이라 지급 배치와 동시에 와도 한쪽만 성공하고, 지급이 먼저면 회수로
   * 이어진다.
   *
   * <p>잠그는 순서는 지갑, 적립 건, 한도, 예산이다. 결제 취소가 먼저 지갑을 잠그므로 지급 배치(지갑 입금, 적립 건, 예산)와 교착하지 않는다.
   *
   * @throws com.back.nbe1214finalhot6.common.exception.BusinessException 회수할 지갑이 없거나 탈퇴했을 때(WAL-001·002). 호출한 쪽
   *     트랜잭션이 롤백된다
   */
  @Transactional
  public void cancel(Long paymentId) {
    CashbackEarning earning = earningRepository.findByPaymentId(paymentId).orElse(null);
    if (earning == null) {
      return;
    }
    // 읽어 둔 엔티티는 낡았을 수 있어서, 전환 결과와 그 뒤의 상태는 UPDATE 결과와 값 조회로 판단한다
    EarningStatus status = earning.getStatus();
    if (status == EarningStatus.PENDING) {
      int changed =
          earningRepository.cancelIfPending(
              earning.getId(),
              LocalDateTime.now(clock),
              EarningStatus.PENDING,
              EarningStatus.CANCELED);
      if (changed == 1) {
        // 예산은 결제 트랜잭션에서 경합이 가장 큰 자원이라 마지막에 되돌린다
        YearMonth month = YearMonth.from(earning.getLimitMonth());
        limitService.release(earning.getWalletId(), month, earning.getAmount());
        budgetService.releaseBudget(month.getYear(), month.getMonthValue(), earning.getAmount());
        return;
      }
      // 그 사이 다른 취소나 지급이 먼저 처리됐다
      status = earningRepository.findStatus(earning.getId()).orElse(null);
    }
    if (status == EarningStatus.PAID) {
      clawBack(earning);
    }
  }

  /** 지급된 적립을 회수한다. 지갑 입금과 같은 지갑·적립 건 순서로 잠근다. */
  private void clawBack(CashbackEarning earning) {
    walletService.debit(
        earning.getWalletId(),
        earning.getAmount(),
        WalletTransactionType.CASHBACK_CLAWBACK,
        earning.getId(),
        true);
    if (earningRepository.clawBackIfPaid(
            earning.getId(), LocalDateTime.now(clock), EarningStatus.PAID, EarningStatus.CLAWED_BACK)
        == 0) {
      // 같은 건을 다른 취소가 먼저 회수했다. 지갑 차감은 같은 키라 한 번만 반영된다
      return;
    }
    limitService.release(
        earning.getWalletId(), YearMonth.from(earning.getLimitMonth()), earning.getAmount());
  }

  private CashbackReserveResponse exclude(
      CashbackReserveRequest command,
      CashbackExclusionReason reason,
      YearMonth month,
      long limit,
      LocalDateTime now) {
    earningRepository.save(
        CashbackEarning.excluded(
            command.paymentId(),
            command.walletId(),
            command.walletUsedAmount(),
            command.requestedAt(),
            reason,
            now));
    return CashbackReserveResponse.excluded(reason, remaining(command.walletId(), month, limit));
  }

  /** 같은 결제로 다시 호출됐을 때, 처음 저장한 결과를 그대로 돌려준다. 남은 한도만 지금 값이다. */
  private CashbackReserveResponse replay(CashbackEarning earning, long limit) {
    YearMonth month = YearMonth.from(earning.getRequestedAt());
    long remaining = remaining(earning.getWalletId(), month, limit);
    if (earning.getExclusionReason() != null) {
      return CashbackReserveResponse.excluded(earning.getExclusionReason(), remaining);
    }
    return new CashbackReserveResponse(
        earning.getAmount(),
        earning.getAppliedRate(),
        earning.getAppliedPolicyId(),
        earning.getPayableAt(),
        CashbackExclusionReason.NONE,
        remaining);
  }

  /** 남은 월 한도. 한도를 사용량보다 낮춘 경우처럼 음수가 되면 0 으로 돌려준다. */
  private long remaining(Long walletId, YearMonth month, long limit) {
    return Math.max(0L, limit - limitService.usedAmount(walletId, month));
  }
}
