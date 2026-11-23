package com.back.nbe1214finalhot6.domain.reconciliation.service;

import com.back.nbe1214finalhot6.domain.reconciliation.dto.ReconciliationOutcome;
import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationType;
import java.time.LocalDate;

/** 대사 종류 하나. 읽기만 하고 데이터를 고치지 않는다. */
public interface ReconciliationCheck {

  ReconciliationType type();

  ReconciliationOutcome check(LocalDate date);
}
