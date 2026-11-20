package com.back.nbe1214finalhot6.domain.wallet.entity;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원의 현재 잔액. 거래가 일어날 때마다 덮어쓰며, 변경 이력은 WalletTransaction에 쌓음. */
@Entity
@Table(name = "wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 회원 ID. 유니크로 회원당 지갑 1개 보장
  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  // 데브머니(충전금) 잔액. 음수 불가
  @Column(nullable = false)
  private long balance;

  // 캐시백 잔액. 지급된 캐시백 회수로 음수가 될 수 있음(캐시백 입금으로만 상계)
  @Column(name = "cashback_balance", nullable = false)
  private long cashbackBalance;

  // 적립 예정 캐시백. 아직 쓸 수 없으므로 사용 가능액에 포함하지 않음
  @Column(name = "pending_cashback", nullable = false)
  private long pendingCashback;

  // 지갑 상태. 탈퇴하면 WITHDRAWN이 되어 입금·차감을 거절함
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private WalletStatus status;

  // 마지막으로 바뀐 시각. 엔티티가 만들지 않고 호출자가 Clock으로 만들어 넘김
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  private Wallet(Long userId, LocalDateTime now) {
    this.userId = userId;
    this.status = WalletStatus.ACTIVE;
    this.updatedAt = now;
  }

  // 모든 잔액 0원, 활성 상태로 생성
  public static Wallet create(Long userId, LocalDateTime now) {
    return new Wallet(userId, now);
  }

  public boolean isWithdrawn() {
    return status == WalletStatus.WITHDRAWN;
  }

  // 탈퇴 처리. 이후 입금·차감은 거절됨
  public void withdraw(LocalDateTime now) {
    this.status = WalletStatus.WITHDRAWN;
    this.updatedAt = now;
  }

  // 입금. 캐시백이면 캐시백 잔액, 아니면 데브머니 잔액에 더하고 반영 후 잔액을 돌려줌
  public long credit(long amount, boolean cashback, LocalDateTime now) {
    long after;
    if (cashback) {
      cashbackBalance = Math.addExact(cashbackBalance, amount);
      after = cashbackBalance;
    } else {
      balance = Math.addExact(balance, amount);
      after = balance;
    }
    updatedAt = now;
    return after;
  }

  // 차감. allowNegative는 캐시백에만 적용하고 데브머니는 항상 음수 불가.
  // 캐시백 잔액이 0 이하일 때의 사용 거절도 "잔액 < 금액" 검사로 함께 막힘
  public long debit(long amount, boolean cashback, boolean allowNegative, LocalDateTime now) {
    long current = cashback ? cashbackBalance : balance;
    boolean mayGoNegative = cashback && allowNegative;
    if (!mayGoNegative && current < amount) {
      throw new BusinessException(
          cashback ? WalletErrorCode.INSUFFICIENT_CASHBACK : WalletErrorCode.INSUFFICIENT_BALANCE);
    }
    long after = current - amount;
    if (cashback) {
      cashbackBalance = after;
    } else {
      balance = after;
    }
    updatedAt = now;
    return after;
  }
}
