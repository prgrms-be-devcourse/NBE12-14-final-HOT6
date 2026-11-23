package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.exception.ReconciliationErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 대사 종류를 하나씩 실행하고 결과를 저장한다. 한 종류의 실패가 다른 종류에 영향을 주지 않도록 트랜잭션은 열지 않고, 실패는 ERROR 결과로 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

  private final List<ReconciliationCheck> checks;
  private final ReconciliationRecorder recorder;
  private final Clock clock;

  public List<ReconciliationResult> run(LocalDate date) {
    if (!date.isBefore(LocalDate.now(clock))) {
      throw new BusinessException(ReconciliationErrorCode.RECONCILIATION_DATE_NOT_ALLOWED);
    }

    List<ReconciliationResult> results = new ArrayList<>();
    for (ReconciliationCheck check : checks) {
      try {
        results.add(runOne(check, date));
      } catch (RuntimeException e) {
        log.error("대사 결과 저장 실패 type={}, date={}", check.type(), date, e);
      }
    }
    return results;
  }

  private ReconciliationResult runOne(ReconciliationCheck check, LocalDate date) {
    try {
      ReconciliationOutcome outcome = check.check(date);
      return recorder.record(check.type(), date, outcome);
    } catch (RuntimeException e) {
      log.error("대사 실행 실패 type={}, date={}", check.type(), date, e);
      return recorder.recordError(check.type(), date, String.valueOf(e.getMessage()));
    }
  }
}
