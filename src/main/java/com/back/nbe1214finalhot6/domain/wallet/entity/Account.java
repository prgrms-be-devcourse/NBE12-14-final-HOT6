package com.back.nbe1214finalhot6.domain.wallet.entity;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 충전의 출처이자 환불의 도착지인 계좌. 회원 1명당 1개.
 *
 * <p>실제 계좌나 가상계좌를 개설할 수 없어서, 외부 연동 없이 잔액만 보관하는 모의 계좌임.
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

  // 가입 시 지급하는 초기 잔액(200만원)
  public static final long INITIAL_BALANCE = 2_000_000L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 회원 ID. 다른 도메인은 연관관계 없이 ID 값으로만 참조, 유니크로 회원당 1개 보장
  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  // 계좌 잔액(원 단위)
  @Column(nullable = false)
  private long balance;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private Account(Long userId, LocalDateTime now) {
    this.userId = userId;
    this.balance = INITIAL_BALANCE;
    this.createdAt = now;
  }

  // 초기 잔액을 채워서 생성. 생성 시각은 호출자가 Clock으로 만들어 넘김
  public static Account create(Long userId, LocalDateTime now) {
    return new Account(userId, now);
  }

  // 출금. 잔액이 모자라면 거절하고, 출금한 뒤의 잔액을 돌려줌
  public long debit(long amount) {
    // 호출 실수(버그)이므로 내부 방어로 막음. 금액 검증(WAL-003)은 서비스가 먼저 함
    if (amount <= 0) {
      throw new IllegalArgumentException("amount는 양수여야 함: " + amount);
    }
    if (balance < amount) {
      throw new BusinessException(WalletErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
    }
    balance = balance - amount;
    return balance;
  }
}
