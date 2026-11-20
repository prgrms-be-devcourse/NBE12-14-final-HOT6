package com.back.nbe1214finalhot6.domain.wallet.entity;

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

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  private Wallet(Long userId, LocalDateTime now) {
    this.userId = userId;
    this.updatedAt = now;
  }

  // 모든 잔액 0원으로 생성. 시각은 호출자가 Clock으로 만들어 넘김
  public static Wallet create(Long userId, LocalDateTime now) {
    return new Wallet(userId, now);
  }
}
