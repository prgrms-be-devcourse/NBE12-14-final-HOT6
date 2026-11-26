package com.back.nbe1214finalhot6.domain.merchant.entity;

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

/** 가맹점 해지 신청 기록. 가맹점당 한 건이며, 신청을 철회할 수 없으므로 지우거나 고치지 않는다. */
@Entity
@Table(name = "merchant_closure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantClosure {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "merchant_id", nullable = false, unique = true)
  private Long merchantId;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private LocalDateTime requestedAt;

  private MerchantClosure(Long merchantId, LocalDateTime requestedAt) {
    this.merchantId = merchantId;
    this.requestedAt = requestedAt;
  }

  public static MerchantClosure create(Long merchantId, LocalDateTime requestedAt) {
    return new MerchantClosure(merchantId, requestedAt);
  }
}
