package com.back.nbe1214finalhot6.domain.cashback.config;

import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import java.time.Clock;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 서버가 시작할 때 올해 연 예산이 없으면 설정한 금액으로 한 번 만든다.
 *
 * <p>운영자 API 로 연 예산을 넣기 전에는 모든 결제의 적립이 "예산 소진"으로 제외되기 때문이다. 이미 있으면 아무것도 하지 않으므로 운영자가 바꾼 값을
 * 덮어쓰지 않고, 여러 번 시작해도 한 건만 남는다. 여러 서버가 동시에 시작해 다른 쪽이 먼저 만든 경우에도 그대로 시작한다. 월 예산은 연 예산 안에서
 * 운영자가 배정한다.
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
    long amount = properties.init().annualBudget();
    try {
      if (budgetService.createAnnualIfAbsent(year, amount)) {
        log.info("{}년 연 예산이 없어 초기값 {}원으로 등록했습니다", year, amount);
      }
    } catch (DataIntegrityViolationException e) {
      // 서버를 동시에 시작해 다른 쪽이 먼저 만든 경우만 넘어간다. 행이 없으면 다른 원인이므로 그대로 던진다
      if (!annualBudgetRepository.existsByBudgetYear(year)) {
        throw e;
      }
      log.info("{}년 연 예산을 다른 쪽에서 먼저 등록해 넘어갑니다", year);
    }
  }
}
