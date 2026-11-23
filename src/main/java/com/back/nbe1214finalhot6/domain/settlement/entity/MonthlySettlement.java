package com.back.nbe1214finalhot6.domain.settlement.entity;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가맹점별 월 정산 지급 건. 지급액은 해당 월 일 정산 확정액의 합이다. */
@Entity
@Table(
    name = "monthly_settlement",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_monthly_settlement_merchant_month",
            columnNames = {"merchant_id", "settlement_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlySettlement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "merchant_id", nullable = false)
  private Long merchantId;

  // 정산 대상 월의 1일
  @Column(name = "settlement_month", nullable = false)
  private LocalDate settlementMonth;

  @Column(name = "payout_amount", nullable = false)
  private long payoutAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MonthlySettlementStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  private MonthlySettlement(
      Long merchantId, LocalDate settlementMonth, long payoutAmount, LocalDateTime now) {
    if (payoutAmount <= 0 || settlementMonth.getDayOfMonth() != 1) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
    this.merchantId = merchantId;
    this.settlementMonth = settlementMonth;
    this.payoutAmount = payoutAmount;
    this.status = MonthlySettlementStatus.PENDING;
    this.createdAt = now;
  }

  public static MonthlySettlement create(
      Long merchantId, LocalDate settlementMonth, long payoutAmount, LocalDateTime now) {
    return new MonthlySettlement(merchantId, settlementMonth, payoutAmount, now);
  }

  public boolean isPaid() {
    return status == MonthlySettlementStatus.PAID;
  }

  public void markPaid(LocalDateTime now) {
    this.status = MonthlySettlementStatus.PAID;
    this.paidAt = now;
  }
}
