package com.back.nbe1214finalhot6.domain.reconciliation.dto;

import java.util.List;

/** 검사 한 번의 결과. 검사한 건수와 불일치 대상을 담는다. */
public record ReconciliationOutcome(long checkedCount, List<String> mismatches) {}
