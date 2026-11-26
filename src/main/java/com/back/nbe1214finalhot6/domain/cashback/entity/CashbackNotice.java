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
import java.time.YearMonth;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/**
 * 이용자에게 보이는 공지. 요율·한도 변경과 적립 중단을 알리며 같은 양식(종류, 제목, 본문, 등록일)을 쓴다. 지우지 않고, 알리던 예약이 취소되면 거둔다.
 *
 * <p>자동 공지의 문구에는 날짜만 쓰고 시각은 쓰지 않는다. 이용자 정보를 담지 않는 공통 안내다.
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

  public static final int TITLE_LENGTH = 100;
  public static final int CONTENT_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private NoticeType type;

  // 제목이 생기기 전에 등록된 공지는 null 이며 종류의 기본 제목으로 보여 준다
  @Column(length = TITLE_LENGTH)
  private String title;

  @Column(nullable = false, length = CONTENT_LENGTH)
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

  // 한도 변경 공지만 있다. 다른 도메인의 설정 키 이름과 이전·새 값
  @Column(name = "setting_key", length = 60)
  private String settingKey;

  @Column(name = "previous_value")
  private Long previousValue;

  @Column(name = "new_value")
  private Long newValue;

  // 기존 행이 있는 테이블에 컬럼을 더하므로 기본값을 둔다
  @Enumerated(EnumType.STRING)
  @ColumnDefault("'PUBLISHED'")
  @Column(nullable = false, length = 20)
  private NoticeStatus status = NoticeStatus.PUBLISHED;

  @ColumnDefault("false")
  @Column(name = "show_on_home", nullable = false)
  private boolean showOnHome;

  // 홈에 띄우는 마지막 날. 홈에 띄우지 않으면 null
  @Column(name = "home_until")
  private LocalDate homeUntil;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private CashbackNotice(
      NoticeType type,
      String title,
      String content,
      LocalDate effectiveFrom,
      LocalDateTime createdAt) {
    this.type = type;
    this.title = title;
    this.content = content;
    this.effectiveFrom = effectiveFrom;
    this.createdAt = createdAt;
  }

  /**
   * 정책 변경 예고를 만든다. 적용 시작일과 새 요율(퍼센트로 표기)을 문구에 쓰며, 적용 시작일이 속한 달의 말일까지 홈에 띄운다.
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
    CashbackNotice notice =
        new CashbackNotice(
            NoticeType.POLICY_CHANGE,
            NoticeType.POLICY_CHANGE.defaultTitle(),
            content,
            from,
            createdAt);
    notice.policyId = policy.getId();
    notice.showOnHomeThroughMonth(from);
    return notice;
  }

  /**
   * 소진 예고를 만든다. 남은 예산이 긴급선 비율 이하가 되거나 중단 예정일이 되면 적립이 멈출 수 있다는 안내이며, 시각은 쓰지 않고 날짜와 %만 쓴다. 그 달
   * 말일까지 홈에 띄운다.
   *
   * @param emergencyLineRate 긴급 중단선(전체 예산 대비, 소수 비율 0.05)
   * @param noticeDays 공지 후 중단까지의 최대 일수
   * @param stopAt 중단 예정일
   */
  public static CashbackNotice budgetNotice(
      BigDecimal emergencyLineRate, int noticeDays, LocalDate stopAt, LocalDateTime createdAt) {
    String content =
        "남은 예산이 "
            + percent(emergencyLineRate)
            + "% 이하이거나 최대 "
            + noticeDays
            + "일 뒤 적립이 중단될 수 있어요. 예산이 더 빨리 소진되면 먼저 종료될 수 있어요. 결제는 정상 이용할 수 있어요";
    CashbackNotice notice =
        new CashbackNotice(
            NoticeType.BUDGET_NOTICE,
            NoticeType.BUDGET_NOTICE.defaultTitle(),
            content,
            createdAt.toLocalDate(),
            createdAt);
    notice.stopAt = stopAt;
    notice.showOnHomeThroughMonth(createdAt.toLocalDate());
    return notice;
  }

  /** 긴급 중단을 만든다. 적립이 이미 중단됐다는 안내이며 그 달 말일까지 홈에 띄운다. */
  public static CashbackNotice emergencyStop(LocalDateTime createdAt) {
    CashbackNotice notice =
        new CashbackNotice(
            NoticeType.EMERGENCY_STOP,
            NoticeType.EMERGENCY_STOP.defaultTitle(),
            "적립이 중단되었어요. 결제는 정상 이용할 수 있어요",
            createdAt.toLocalDate(),
            createdAt);
    notice.showOnHomeThroughMonth(createdAt.toLocalDate());
    return notice;
  }

  /**
   * 한도 변경 공지를 만든다.
   *
   * @param settingKey 바꾸는 설정 키 이름
   * @param effectiveFrom 새 값이 적용되는 첫 날
   * @param homeUntil 홈에 띄우는 마지막 날. showOnHome 이 아니면 저장하지 않는다
   */
  public static CashbackNotice limitChange(
      String settingKey,
      long previousValue,
      long newValue,
      LocalDate effectiveFrom,
      String title,
      String content,
      boolean showOnHome,
      LocalDate homeUntil,
      LocalDateTime createdAt) {
    CashbackNotice notice =
        new CashbackNotice(NoticeType.LIMIT_CHANGE, title, content, effectiveFrom, createdAt);
    notice.showOnHome = showOnHome;
    notice.homeUntil = showOnHome ? homeUntil : null;
    notice.settingKey = settingKey;
    notice.previousValue = previousValue;
    notice.newValue = newValue;
    return notice;
  }

  private void showOnHomeThroughMonth(LocalDate date) {
    this.showOnHome = true;
    this.homeUntil = YearMonth.from(date).atEndOfMonth();
  }

  /** 보여 줄 제목. 제목이 없으면 종류의 기본 제목이다. */
  public String displayTitle() {
    return title != null ? title : type.defaultTitle();
  }

  /** 알리던 예약이 취소되어 공지를 거둔다. */
  public void withdraw() {
    status = NoticeStatus.WITHDRAWN;
  }

  /** 소수 비율(0.05)을 퍼센트 문자열(5)로 바꾼다. 소수가 있으면 그대로 둔다(0.035 -> 3.5). */
  static String percent(BigDecimal rate) {
    return rate.movePointRight(2).stripTrailingZeros().toPlainString();
  }
}
