package com.back.nbe1214finalhot6.domain.merchant.entity;

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

/**
 * 해지 정산에서 부족분(취소·환수)을 갚으려고 달의 지급액에서 차감한 금액. 한 가맹점의 한 달에 한 건이며 고치지 않는다.
 *
 * <p>차감한 달은 월 정산 지급액이 일 정산 확정액의 합보다 작거나, 전액 차감되어 월 정산 건이 아예 없다. 월 지급 대사가 이 금액을 빼고 비교한다.
 */
@Entity
@Table(
    name = "merchant_closure_deduction",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_closure_deduction_merchant_month",
            columnNames = {"merchant_id", "settlement_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantClosureDeduction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "merchant_id", nullable = false)
  private Long merchantId;

  // 차감한 달의 1일
  @Column(name = "settlement_month", nullable = false)
  private LocalDate settlementMonth;

  @Column(nullable = false)
  private long amount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private MerchantClosureDeduction(
      Long merchantId, LocalDate settlementMonth, long amount, LocalDateTime createdAt) {
    if (amount <= 0 || settlementMonth.getDayOfMonth() != 1) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
    this.merchantId = merchantId;
    this.settlementMonth = settlementMonth;
    this.amount = amount;
    this.createdAt = createdAt;
  }

  public static MerchantClosureDeduction create(
      Long merchantId, LocalDate settlementMonth, long amount, LocalDateTime createdAt) {
    return new MerchantClosureDeduction(merchantId, settlementMonth, amount, createdAt);
  }
}
