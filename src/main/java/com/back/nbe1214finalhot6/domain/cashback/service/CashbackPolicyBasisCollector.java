package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.dto.ForecastResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyBasis;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 정책을 바꾸기 직전의 예산과 소진 예측을 모은다. 읽기만 한다. */
@Component
@RequiredArgsConstructor
class CashbackPolicyBasisCollector {

  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackForecastService forecastService;
  private final Clock clock;

  /**
   * 호출한 트랜잭션 안에서 읽는다. 합계 조회 동안 결제가 기다리지 않도록 월 예산 행을 잠그기 전에 부른다. 월 예산이 없을 때 예측을 부르면 예외가
   * 트랜잭션을 롤백 전용으로 만들어 인하가 저장되지 않으므로, 예산이 있는지 먼저 확인한다.
   *
   * @return 직전 요율이 빈 근거. 호출한 쪽이 {@link PolicyBasis#withPreviousRate} 로 채운다
   */
  PolicyBasis collect() {
    LocalDate today = LocalDate.now(clock);
    CashbackMonthlyBudget budget =
        monthlyBudgetRepository
            .findByBudgetYearAndBudgetMonth(today.getYear(), today.getMonthValue())
            .orElse(null);
    if (budget == null) {
      return PolicyBasis.rateOnly(null);
    }
    ForecastResponse forecast = forecastService.forecast(null, null);
    return new PolicyBasis(
        null,
        budget.getBudgetYear(),
        budget.getBudgetMonth(),
        budget.getAllocatedAmount(),
        budget.getPaidAmount(),
        budget.getReservedAmount(),
        budget.remainingAmount(),
        budget.getContingencyAmount(),
        forecast.assumptions().baseDailyDemand(),
        forecast.fast().exhaustionDate(),
        forecast.mid().exhaustionDate(),
        forecast.slow().exhaustionDate(),
        forecast.mid().noticeLineDate(),
        forecast.assumptions().modelVersion());
  }
}
