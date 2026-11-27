package com.back.nbe1214finalhot6.domain.cashback.entity;

import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 그날 계산한 소진 예측. 예측이 실제와 얼마나 달랐는지 나중에 비교한다. 하루 한 건이고 INSERT only 다. */
@Entity
@Table(
    name = "cashback_forecast_snapshot",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_cashback_forecast_snapshot_date",
            columnNames = "snapshot_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashbackForecastSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "snapshot_date", nullable = false)
  private LocalDate snapshotDate;

  /** 저장한 순간의 남은 예산. */
  @Column(name = "remaining_amount", nullable = false)
  private long remainingAmount;

  @Column(name = "contingency_amount", nullable = false)
  private long contingencyAmount;

  /** 하루 예상 수요(원). 빠름·중간·느림. */
  @Column(name = "fast_daily_demand", nullable = false, precision = 19, scale = 2)
  private BigDecimal fastDailyDemand;

  @Column(name = "mid_daily_demand", nullable = false, precision = 19, scale = 2)
  private BigDecimal midDailyDemand;

  @Column(name = "slow_daily_demand", nullable = false, precision = 19, scale = 2)
  private BigDecimal slowDailyDemand;

  /** 예상 소진일. 그 달 안에 소진되지 않으면 null. */
  @Column(name = "fast_exhaustion_date")
  private LocalDate fastExhaustionDate;

  @Column(name = "mid_exhaustion_date")
  private LocalDate midExhaustionDate;

  @Column(name = "slow_exhaustion_date")
  private LocalDate slowExhaustionDate;

  /** 중간 시나리오의 대비금 도달일. 그 달 안에 닿지 않으면 null. */
  @Column(name = "notice_line_date")
  private LocalDate noticeLineDate;

  @Column(name = "model_version", nullable = false, length = 20)
  private String modelVersion;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  public static CashbackForecastSnapshot of(ForecastResponse forecast, LocalDateTime createdAt) {
    CashbackForecastSnapshot snapshot = new CashbackForecastSnapshot();
    snapshot.snapshotDate = forecast.assumptions().asOf();
    snapshot.remainingAmount = forecast.remainingAmount();
    snapshot.contingencyAmount = forecast.contingencyAmount();
    snapshot.fastDailyDemand = forecast.fast().dailyDemand();
    snapshot.midDailyDemand = forecast.mid().dailyDemand();
    snapshot.slowDailyDemand = forecast.slow().dailyDemand();
    snapshot.fastExhaustionDate = forecast.fast().exhaustionDate();
    snapshot.midExhaustionDate = forecast.mid().exhaustionDate();
    snapshot.slowExhaustionDate = forecast.slow().exhaustionDate();
    snapshot.noticeLineDate = forecast.mid().noticeLineDate();
    snapshot.modelVersion = forecast.assumptions().modelVersion();
    snapshot.createdAt = createdAt;
    return snapshot;
  }
}
