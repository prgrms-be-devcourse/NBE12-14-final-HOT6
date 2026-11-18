package com.back.nbe1214finalhot6.domain.auth.entity;

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
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, unique = true, length = 100)
  private String email;

  @Column(nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, unique = true, length = 11)
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

  public static User create(
      String name, String email, String phone, String passwordHash, String pinHash, UserRole role) {
    return new User(name, email, phone, passwordHash, pinHash, role);
  }
}
