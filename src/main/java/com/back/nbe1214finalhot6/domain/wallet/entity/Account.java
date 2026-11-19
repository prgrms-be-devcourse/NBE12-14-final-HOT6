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
import org.hibernate.annotations.CreationTimestamp;

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

  // 가입 시 지급하는 초기 잔액(100만원)
  public static final long INITIAL_BALANCE = 1_000_000L;

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
  @CreationTimestamp
  private LocalDateTime createdAt;

  private Account(Long userId) {
    this.userId = userId;
    this.balance = INITIAL_BALANCE;
  }

  // 초기 잔액을 채워서 생성
  public static Account create(Long userId) {
    return new Account(userId);
  }
}
