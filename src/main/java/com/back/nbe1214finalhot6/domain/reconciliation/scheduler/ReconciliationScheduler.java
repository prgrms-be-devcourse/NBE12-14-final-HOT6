package com.back.nbe1214finalhot6.domain.reconciliation.scheduler;

import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.service.ReconciliationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

  private final ReconciliationService reconciliationService;
  private final Clock clock;

  // 일 정산(00:10)과 월 정산(00:30)이 끝난 뒤에 어제 기준으로 대사한다.
  @Scheduled(cron = "0 50 0 * * *", zone = "Asia/Seoul")
  public void reconcileYesterday() {
    LocalDate date = LocalDate.now(clock).minusDays(1);
    List<ReconciliationResult> results = reconciliationService.run(date);
    long problems =
        results.stream()
            .filter(result -> result.getStatus() != ReconciliationStatus.MATCHED)
            .count();
    if (problems > 0) {
      log.warn("대사 이상 감지 date={}, 이상 종류 수={}", date, problems);
    } else {
      log.info("대사 완료 date={}, 종류 수={}", date, results.size());
    }
  }
}
