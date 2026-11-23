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

/**
 * 가맹점이 지갑에서 출금한 돈을 받는 모의계좌. 가맹점당 1개.
 *
 * <p>외부 연동 없이 잔액만 보관하며, 이동 이력은 MerchantWalletTransaction에만 남김.
 */
@Entity
@Table(name = "merchant_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "merchant_id", nullable = false, unique = true)
  private Long merchantId;

  // 계좌 잔액. 0원으로 시작
  @Column(nullable = false)
  private long balance;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private MerchantAccount(Long merchantId, LocalDateTime now) {
    this.merchantId = merchantId;
    this.createdAt = now;
  }

  public static MerchantAccount create(Long merchantId, LocalDateTime now) {
    return new MerchantAccount(merchantId, now);
  }

  public void deposit(long amount) {
    this.balance += amount;
  }
}
