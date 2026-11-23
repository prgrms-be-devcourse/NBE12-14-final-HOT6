package com.back.nbe1214finalhot6.domain.settlement.entity;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 가맹점별 일 정산 장부. 확정되면 수정하지 않는다. */
@Entity
@Table(
    name = "daily_settlement",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_daily_settlement_merchant_date",
            columnNames = {"merchant_id", "settlement_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySettlement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "merchant_id", nullable = false)
  private Long merchantId;

  @Column(name = "settlement_date", nullable = false)
  private LocalDate settlementDate;

  // 그날 완료된 결제 총액
  @Column(name = "payment_total", nullable = false)
  private long paymentTotal;

  // 그날 취소된 결제 총액
  @Column(name = "cancel_total", nullable = false)
  private long cancelTotal;

  @Column(name = "net_sales", nullable = false)
  private long netSales;

  // 전일에서 넘어온 부족분. 0 이하
  @Column(name = "carry_in", nullable = false)
  private long carryIn;

  @Column(name = "confirmed_amount", nullable = false)
  private long confirmedAmount;

  // 다음 날로 넘기는 부족분. 0 이하
  @Column(name = "carry_out", nullable = false)
  private long carryOut;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  private DailySettlement(
      Long merchantId,
      LocalDate settlementDate,
      long paymentTotal,
      long cancelTotal,
      long carryIn,
      LocalDateTime now) {
    if (paymentTotal < 0 || cancelTotal < 0 || carryIn > 0) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
    long net = paymentTotal - cancelTotal;
    long total = net + carryIn;
    this.merchantId = merchantId;
    this.settlementDate = settlementDate;
    this.paymentTotal = paymentTotal;
    this.cancelTotal = cancelTotal;
    this.netSales = net;
    this.carryIn = carryIn;
    this.confirmedAmount = Math.max(total, 0);
    this.carryOut = Math.min(total, 0);
    this.createdAt = now;
  }

  public static DailySettlement create(
      Long merchantId,
      LocalDate settlementDate,
      long paymentTotal,
      long cancelTotal,
      long carryIn,
      LocalDateTime now) {
    return new DailySettlement(merchantId, settlementDate, paymentTotal, cancelTotal, carryIn, now);
  }
}
