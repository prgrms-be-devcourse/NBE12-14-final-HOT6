package com.back.nbe1214finalhot6.domain.cashback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이용자의 월 적립 한도 사용량. 이용자(지갑)와 월마다 한 행이다.
 *
 * <p>한도 금액은 행에 저장하지 않는다. 운영자가 바꿀 수 있는 설정값이라 확보할 때마다 호출한 쪽이 넘기며, 이 행에는 적립 예정을 포함해 이미 쓴 금액만 둔다.
 * 월은 결제를 최초로 접수한 시각의 한국 시간 월이고, 새 달이 되면 새 행이 생겨서 초기화 작업이 필요 없다.
 *
 * <p>행은 {@code CashbackMonthlyLimitRepository} 의 쿼리로만 만들고 바꾼다. 읽고 판단하는 사이에 다른 결제가 끼어들지 못하게 하려는 것이다.
 */
@Entity
@Table(
    name = "cashback_monthly_limit",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_cashback_monthly_limit_wallet_month",
            columnNames = {"wallet_id", "limit_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackMonthlyLimit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 다른 도메인의 엔티티는 연관관계 없이 ID 로만 참조한다
  @Column(name = "wallet_id", nullable = false)
  private Long walletId;

  // 그 달의 1일. 달만 구분하면 되어서 날짜 컬럼에 1일로 저장한다
  @Column(name = "limit_month", nullable = false)
  private LocalDate limitMonth;

  // 적립 예정을 포함한 사용량
  @Column(name = "used_amount", nullable = false)
  private long usedAmount;
}
