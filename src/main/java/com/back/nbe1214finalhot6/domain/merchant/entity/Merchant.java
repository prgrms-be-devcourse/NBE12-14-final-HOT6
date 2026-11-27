package com.back.nbe1214finalhot6.domain.merchant.entity;

import com.back.nbe1214finalhot6.domain.merchant.geocoding.Location;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "merchant",
    uniqueConstraints = @UniqueConstraint(name = Merchant.OWNER_UNIQUE, columnNames = "owner_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant {

  // 점주 한 명당 가맹점 하나. 동시에 두 번 등록해도 DB 가 막는다(owner_id 가 null 인 가맹점은 여러 개 가능)
  public static final String OWNER_UNIQUE = "uk_merchant_owner";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 등록한 사장님의 users.id. 공공데이터로 적재한 가맹점은 사장님이 없으므로 null(점주당 하나만 허용)
  @Column(name = "owner_id")
  private Long ownerId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 200)
  private String address;

  @Column(name = "cashback_eligible", nullable = false)
  private boolean cashbackEligible;

  // 주소를 좌표로 바꾼 값. 변환하지 못했거나 기존 가맹점이면 둘 다 null 이고, 지도에는 찍히지 않는다
  @Column private Double latitude;

  @Column private Double longitude;

  // 상태는 조건부 UPDATE 로만 바꾼다(MerchantRepository.updateStatusIfCurrent). 기존 행은 ACTIVE 로 채운다
  @Enumerated(EnumType.STRING)
  @ColumnDefault("'ACTIVE'")
  @Column(nullable = false, length = 20)
  private MerchantStatus status = MerchantStatus.ACTIVE;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private Merchant(
      Long ownerId,
      String name,
      String address,
      boolean cashbackEligible,
      Double latitude,
      Double longitude) {
    this.ownerId = ownerId;
    this.name = name;
    this.address = address;
    this.cashbackEligible = cashbackEligible;
    this.latitude = latitude;
    this.longitude = longitude;
    this.createdAt = LocalDateTime.now();
  }

  public static Merchant create(
      Long ownerId,
      String name,
      String address,
      boolean cashbackEligible) {
    return new Merchant(ownerId, name, address, cashbackEligible, null, null);
  }

  public static Merchant create(
      Long ownerId,
      String name,
      String address,
      boolean cashbackEligible,
      Location location) {
    return new Merchant(
        ownerId,
        name,
        address,
        cashbackEligible,
        location == null ? null : location.latitude(),
        location == null ? null : location.longitude());
  }

  public void update(String name, String address) {
    if (name != null) {
      this.name = name;
    }
    if (address != null) {
      this.address = address;
    }
  }

  /** 좌표를 바꾼다. 위치가 null 이면 비운다(주소는 바뀌었는데 새 좌표를 알 수 없을 때). */
  public void updateLocation(Location location) {
    this.latitude = location == null ? null : location.latitude();
    this.longitude = location == null ? null : location.longitude();
  }
}
