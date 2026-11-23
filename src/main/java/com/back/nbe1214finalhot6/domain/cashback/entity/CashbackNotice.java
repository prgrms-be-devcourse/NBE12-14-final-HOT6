package com.back.nbe1214finalhot6.domain.cashback.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이용자에게 보이는 캐시백 공지. 요율 변경 예고처럼 앱의 "내 캐시백"과 알림·공지 목록이 보여 주는 안내이며 INSERT only 다.
 *
 * <p>문구에는 날짜만 쓰고 시각은 쓰지 않는다. 이용자나 금액 정보를 담지 않는 공통 안내다.
 */
@Entity
@Table(
    name = "cashback_notice",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_cashback_notice_policy_type",
            columnNames = {"policy_id", "type"}),
    indexes = @Index(name = "idx_cashback_notice_created", columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackNotice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NoticeType type;

  @Column(nullable = false, length = 500)
  private String content;

  // 공지가 알리는 내용이 적용되는 첫 날
  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  // 중단 예정일. 중단과 관련 없는 공지는 null
  @Column(name = "stop_at")
  private LocalDate stopAt;

  // 정책 변경 공지만 있다. 같은 정책에 같은 종류의 공지가 두 번 생기지 않게 한다
  @Column(name = "policy_id")
  private Long policyId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private CashbackNotice(
      NoticeType type,
      String content,
      LocalDate effectiveFrom,
      LocalDate stopAt,
      Long policyId,
      LocalDateTime createdAt) {
    this.type = type;
    this.content = content;
    this.effectiveFrom = effectiveFrom;
    this.stopAt = stopAt;
    this.policyId = policyId;
    this.createdAt = createdAt;
  }

  /**
   * 정책 변경 예고를 만든다. 적용 시작일과 새 요율(퍼센트로 표기)을 문구에 쓴다.
   *
   * @param policy 새로 만든 요율 구간. 저장되어 id 가 있어야 한다
   */
  public static CashbackNotice policyChange(CashbackPolicy policy, LocalDateTime createdAt) {
    LocalDate from = policy.getStartsAt().toLocalDate();
    String content =
        from.getMonthValue()
            + "월 "
            + from.getDayOfMonth()
            + "일부터 적립률이 "
            + percent(policy.getRate())
            + "%로 바뀌어요. 이미 접수된 결제는 약속한 금액 그대로 적립돼요";
    return new CashbackNotice(
        NoticeType.POLICY_CHANGE, content, from, null, policy.getId(), createdAt);
  }

  /** 소수 비율(0.05)을 퍼센트 문자열(5)로 바꾼다. 소수가 있으면 그대로 둔다(0.035 -> 3.5). */
  static String percent(BigDecimal rate) {
    return rate.movePointRight(2).stripTrailingZeros().toPlainString();
  }
}
