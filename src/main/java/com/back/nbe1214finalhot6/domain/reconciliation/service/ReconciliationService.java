package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.exception.ReconciliationErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 대사 종류를 하나씩 실행하고 결과를 저장한다. 한 종류의 실패가 다른 종류에 영향을 주지 않도록 트랜잭션은 열지 않고, 실패는 ERROR 결과로 남긴다.
 *
 * <p>ERROR 결과마저 저장하지 못한 종류도 응답에서 빼지 않고, 저장되지 않은 ERROR 결과(id 없음)로 돌려준다. 빼 버리면 호출한 쪽이 일부 종류만 돌았다는 것을 알 수 없다.
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
        results.add(
            ReconciliationResult.error(
                check.type(),
                date,
                "결과를 저장하지 못했습니다. 서버 로그를 확인해 주세요",
                LocalDateTime.now(clock)));
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
