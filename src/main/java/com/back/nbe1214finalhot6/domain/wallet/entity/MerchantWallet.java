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

/** 가맹점이 정산금을 받는 지갑. 변경 이력은 MerchantWalletTransaction에 쌓음. */
@Entity
@Table(name = "merchant_wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantWallet {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 가맹점 ID. 유니크로 가맹점당 지갑 1개 보장
  @Column(name = "merchant_id", nullable = false, unique = true)
  private Long merchantId;

  // 잔액. 음수 불가
  @Column(nullable = false)
  private long balance;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  private MerchantWallet(Long merchantId, LocalDateTime now) {
    this.merchantId = merchantId;
    this.updatedAt = now;
  }

  public static MerchantWallet create(Long merchantId, LocalDateTime now) {
    return new MerchantWallet(merchantId, now);
  }

  // 금액 검증과 잔액 부족 판단은 서비스가 하고, 여기서는 잔액만 바꿈
  public void deposit(long amount, LocalDateTime now) {
    this.balance += amount;
    this.updatedAt = now;
  }

  public void withdraw(long amount, LocalDateTime now) {
    this.balance -= amount;
    this.updatedAt = now;
  }
}
