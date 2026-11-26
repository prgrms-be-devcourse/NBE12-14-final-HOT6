package com.back.nbe1214finalhot6.domain.cashback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지급 배치가 처리한 적립 한 건의 기록. 적립 값은 실행 시점의 것을 복사해 두어, 나중에 취소·회수돼도 당시 기록이 남는다. INSERT only 다.
 */
@Entity
@Table(
    name = "cashback_payout_run_item",
    indexes = @Index(name = "idx_cashback_payout_run_item_run", columnList = "run_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackPayoutRunItem {

  private static final int MESSAGE_LENGTH = 200;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "earning_id", nullable = false)
  private Long earningId;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Column(name = "wallet_id", nullable = false)
  private Long walletId;

  @Column(nullable = false)
  private long amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PayoutItemResult result;

  /** 실패했을 때의 에러 코드. 그 밖에는 null 이다. */
  @Column(name = "error_code", length = 20)
  private String errorCode;

  /** 실패했을 때의 안내 문구. 그 밖에는 null 이다. */
  @Column(name = "error_message", length = MESSAGE_LENGTH)
  private String errorMessage;

  private CashbackPayoutRunItem(
      Long runId,
      CashbackEarning earning,
      PayoutItemResult result,
      String errorCode,
      String errorMessage) {
    this.runId = runId;
    this.earningId = earning.getId();
    this.paymentId = earning.getPaymentId();
    this.walletId = earning.getWalletId();
    this.amount = earning.getAmount();
    this.result = result;
    this.errorCode = errorCode;
    this.errorMessage =
        errorMessage == null || errorMessage.length() <= MESSAGE_LENGTH
            ? errorMessage
            : errorMessage.substring(0, MESSAGE_LENGTH);
  }

  public static CashbackPayoutRunItem of(
      Long runId,
      CashbackEarning earning,
      PayoutItemResult result,
      String errorCode,
      String errorMessage) {
    return new CashbackPayoutRunItem(runId, earning, result, errorCode, errorMessage);
  }
}
