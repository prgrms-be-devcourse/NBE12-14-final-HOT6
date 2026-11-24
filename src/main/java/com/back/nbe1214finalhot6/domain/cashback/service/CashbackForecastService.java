package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastAssumptions;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastPoint;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastScenario;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월 예산의 소진 예측. 운영자 대시보드가 "이 속도면 예산이 언제 소진되는지"를 보여 줄 때 쓰는 참고 정보이며, 읽기만 하고 아무것도 저장하지 않는다.
 *
 * <p>수요는 기준일(오늘)을 뺀 최근 7일의 일평균 신규 적립 확보액이다. 취소된 건은 빼고, 지급 후 회수된 건은 예산을 되돌리지 않았으므로 넣는다. 여기에 수요 배율을
 * 곱한 값이 중앙이고, 범위 폭만큼 위아래로 벌린 값이 빠름·느림이다. 계절 지수는 쓰지 않는다(1.0).
 */
@Service
@RequiredArgsConstructor
public class CashbackForecastService {

  static final String DEMAND_BASIS = "RECENT_7D_AVG";
  static final String MODEL_VERSION = "v1";
  private static final int DEMAND_DAYS = 7;
  // 취소(CANCELED)는 예산을 돌려줬으므로 수요에서 뺀다. 제외 건은 적립액이 0 이다
  private static final Set<EarningStatus> DEMAND_STATUSES =
      Set.of(EarningStatus.PENDING, EarningStatus.PAID, EarningStatus.CLAWED_BACK);

  private final CashbackBudgetService budgetService;
  private final CashbackEarningRepository earningRepository;
  private final OperatorSettingService settingService;
  private final CashbackProperties properties;
  private final Clock clock;

  /**
   * 이번 달 월 예산의 소진 예측. 예측은 이번 달만 계산하고, year·month 를 모두 생략하면 이번 달이다.
   *
   * @throws BusinessException 하나만 줬을 때, 월이 1~12 가 아닐 때, 이번 달이 아닐 때(CSH-007), 월 예산이 없을 때(CSH-004)
   */
  @Transactional(readOnly = true)
  public ForecastResponse forecast(Integer requestedYear, Integer requestedMonth) {
    LocalDate asOf = LocalDate.now(clock);
    if ((requestedYear == null) != (requestedMonth == null)) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_BUDGET, "year 와 month 는 함께 주거나 모두 생략해야 합니다");
    }
    if (requestedMonth != null && (requestedMonth < 1 || requestedMonth > 12)) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "월은 1~12 여야 합니다");
    }
    YearMonth target =
        requestedYear == null ? YearMonth.from(asOf) : YearMonth.of(requestedYear, requestedMonth);
    if (!target.equals(YearMonth.from(asOf))) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "예측은 이번 달만 계산할 수 있습니다");
    }
    int year = target.getYear();
    int month = target.getMonthValue();
    MonthlyBudgetResponse budget = budgetService.getMonthly(year, month);

    BigDecimal baseDaily = baseDailyDemand(asOf);
    CashbackProperties.Forecast forecast = properties.forecast();
    BigDecimal mid = scale(baseDaily.multiply(forecast.demandMultiplier()));
    BigDecimal fast = scale(mid.multiply(BigDecimal.ONE.add(forecast.rangeRatio())));
    BigDecimal slow = scale(mid.multiply(BigDecimal.ONE.subtract(forecast.rangeRatio())).max(BigDecimal.ZERO));

    LocalDate monthEnd = target.atEndOfMonth();
    long remaining = budget.remainingAmount();
    long contingency = budget.contingencyAmount();
    ForecastCalculator.Result fastResult = ForecastCalculator.calculate(remaining, contingency, fast, asOf, monthEnd);
    ForecastCalculator.Result midResult = ForecastCalculator.calculate(remaining, contingency, mid, asOf, monthEnd);
    ForecastCalculator.Result slowResult = ForecastCalculator.calculate(remaining, contingency, slow, asOf, monthEnd);

    List<ForecastPoint> points = new ArrayList<>();
    for (int i = 0; i < midResult.remainingByDay().size(); i++) {
      points.add(
          new ForecastPoint(
              asOf.plusDays(i + 1L),
              fastResult.remainingByDay().get(i),
              midResult.remainingByDay().get(i),
              slowResult.remainingByDay().get(i)));
    }

    long limit = settingService.getLong(SettingKey.CASHBACK_USER_MONTHLY_LIMIT);
    long guaranteedUsers = limit <= 0 ? 0L : Math.max(0L, remaining) / limit;
    return new ForecastResponse(
        year,
        month,
        remaining,
        contingency,
        guaranteedUsers,
        new ForecastAssumptions(
            DEMAND_BASIS,
            baseDaily,
            forecast.demandMultiplier(),
            forecast.rangeRatio(),
            MODEL_VERSION,
            asOf),
        new ForecastScenario(fast, fastResult.exhaustionDate(), fastResult.noticeLineDate()),
        new ForecastScenario(mid, midResult.exhaustionDate(), midResult.noticeLineDate()),
        new ForecastScenario(slow, slowResult.exhaustionDate(), slowResult.noticeLineDate()),
        points);
  }

  /** 기준일을 뺀 최근 7일(기준일 7일 전 00:00 이상, 기준일 00:00 미만)의 일평균 신규 적립 확보액. */
  private BigDecimal baseDailyDemand(LocalDate asOf) {
    long total =
        earningRepository.sumAmountRequestedBetween(
            asOf.minusDays(DEMAND_DAYS).atStartOfDay(), asOf.atStartOfDay(), DEMAND_STATUSES);
    return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(DEMAND_DAYS), 2, RoundingMode.HALF_UP);
  }

  private static BigDecimal scale(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}
