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

@Entity
@Table(name = "merchant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 등록한 사장님의 users.id. 공공데이터로 적재한 가맹점은 사장님이 없으므로 null
  @Column(name = "owner_id")
  private Long ownerId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 200)
  private String address;

  @Column(name = "cashback_eligible", nullable = false)
  private boolean cashbackEligible;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private Merchant(
      Long ownerId,
      String name,
      String address,
      boolean cashbackEligible) {
    this.ownerId = ownerId;
    this.name = name;
    this.address = address;
    this.cashbackEligible = cashbackEligible;
    this.createdAt = LocalDateTime.now();
  }

  public static Merchant create(
      Long ownerId,
      String name,
      String address,
      boolean cashbackEligible) {
    return new Merchant(ownerId, name, address, cashbackEligible);
  }

  public void update(String name, String address) {
    if (name != null) {
      this.name = name;
    }
    if (address != null) {
      this.address = address;
    }
  }
}
