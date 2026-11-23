package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import com.back.nbe1214finalhot6.domain.reconciliation.repository.ReconciliationResultRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대사 결과를 한 건씩 저장한다. 호출하는 쪽은 트랜잭션을 열지 않아서 결과마다 따로 커밋된다. */
@Service
@RequiredArgsConstructor
public class ReconciliationRecorder {

  private final ReconciliationResultRepository resultRepository;
  private final Clock clock;

  @Transactional
  public ReconciliationResult record(
      ReconciliationType type, LocalDate date, ReconciliationOutcome outcome) {
    return resultRepository.save(
        ReconciliationResult.of(
            type, date, outcome.checkedCount(), outcome.mismatches(), LocalDateTime.now(clock)));
  }

  @Transactional
  public ReconciliationResult recordError(ReconciliationType type, LocalDate date, String message) {
    return resultRepository.save(
        ReconciliationResult.error(type, date, message, LocalDateTime.now(clock)));
  }
}
