package com.back.nbe1214finalhot6.domain.cashback.config;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import java.time.Clock;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버가 시작할 때 올해 연 예산이 없으면 설정한 금액으로 한 번 만든다.
 *
 * <p>운영자 API 로 연 예산을 넣기 전에는 모든 결제의 적립이 "예산 소진"으로 제외되기 때문이다. 이미 있으면 아무것도 하지 않으므로 운영자가 바꾼 값을
 * 덮어쓰지 않고, 여러 번 시작해도 한 건만 남는다. 월 예산은 연 예산 안에서 운영자가 배정한다.
 *
 * <p>{@code cashback.init.enabled} 가 true 일 때만 동작한다(기본 false, 테스트는 꺼 둔다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CashbackInitData implements ApplicationRunner {

  private final CashbackProperties properties;
  private final CashbackAnnualBudgetRepository annualBudgetRepository;
  private final CashbackBudgetService budgetService;
  private final Clock clock;

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.init().enabled()) {
      return;
    }
    int year = Year.now(clock).getValue();
    if (annualBudgetRepository.existsByBudgetYear(year)) {
      return;
    }
    budgetService.putAnnual(year, new AnnualBudgetRequest(properties.init().annualBudget()));
    log.info("{}년 연 예산이 없어 초기값 {}원으로 등록했습니다", year, properties.init().annualBudget());
  }
}
