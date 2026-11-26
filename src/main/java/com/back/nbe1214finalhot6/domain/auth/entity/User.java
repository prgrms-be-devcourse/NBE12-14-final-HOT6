package com.back.nbe1214finalhot6.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "users",
    // 이메일·전화번호의 중복 제약(활동 중인 계정끼리, 계정 종류별)은 부분 유니크 인덱스라 schema.sql 에서 만든다.
    // 아래는 탈퇴한 계정까지 찾는 조회(로그인, 가입 중복 확인)가 쓰는 인덱스다
    indexes = {
      @Index(name = "idx_users_email", columnList = "email"),
      @Index(name = "idx_users_phone", columnList = "phone")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 100)
  private String email;

  @Column(nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 11)
  private String phone;

  @Column(nullable = false, length = 255)
  private String pinHash;

  @Column(nullable = false)
  private int pinFailCount;

  private LocalDateTime pinLockedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  private LocalDateTime withdrawnAt;

  @Column(nullable = false, updatable = false)
  @CreationTimestamp
  private LocalDateTime createdAt;

  private User(
      String name, String email, String phone, String passwordHash, String pinHash, UserRole role) {
    this.name = name;
    this.email = email;
    this.phone = phone;
    this.passwordHash = passwordHash;
    this.pinHash = pinHash;
    this.role = role;
    this.status = UserStatus.ACTIVE;
  }

  /**
   * PIN 잠금이 유지되는 시간. 이 시간이 지나면 잠금이 자동으로 풀린다.
   *
   * <p>임시 방식이다. 스펙(AUTH-04)의 원래 방식은 이메일 인증번호로 해제한 뒤 PIN 을 재설정하는 것인데 MVP(11/24) 일정에 이메일 발송이 없어서, 스펙의 대체안(30분 자동
   * 해제)을 쓴다. MVP 이후에는 이메일 인증으로 해제(U7)하는 방식으로 바꾼다. 그때 이 자동 해제는 없애거나 이메일 인증이 안 될 때의 보조 수단으로만 남긴다.
   */
  public static final Duration PIN_LOCK_DURATION = Duration.ofMinutes(30);

  /** 지금(now) PIN 이 잠겨 있는지. 잠긴 지 {@link #PIN_LOCK_DURATION}이 지났으면 잠겨 있지 않은 것으로 본다. */
  public boolean isPinLocked(LocalDateTime now) {
    return pinLockedAt != null && now.isBefore(pinLockedAt.plus(PIN_LOCK_DURATION));
  }

  /** 가맹점을 등록한 회원을 점주로 올린다. 이미 점주면 그대로 두고, 운영자는 점주가 될 수 없다. */
  public void promoteToOwner() {
    if (role == UserRole.ADMIN) {
      throw new IllegalStateException("운영자는 점주가 될 수 없음");
    }
    this.role = UserRole.OWNER;
  }

  public static User create(
      String name, String email, String phone, String passwordHash, String pinHash, UserRole role) {
    return new User(name, email, phone, passwordHash, pinHash, role);
  }
}
