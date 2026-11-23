package com.back.nbe1214finalhot6.domain.reconciliation.dto;

import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationStatus;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReconciliationResultResponse(
    Long id,
    ReconciliationType type,
    LocalDate targetDate,
    ReconciliationStatus status,
    long checkedCount,
    long mismatchCount,
    String mismatchRefs,
    String errorMessage,
    LocalDateTime executedAt) {

  public static ReconciliationResultResponse from(ReconciliationResult result) {
    return new ReconciliationResultResponse(
        result.getId(),
        result.getType(),
        result.getTargetDate(),
        result.getStatus(),
        result.getCheckedCount(),
        result.getMismatchCount(),
        result.getMismatchRefs(),
        result.getErrorMessage(),
        result.getExecutedAt());
  }
}
