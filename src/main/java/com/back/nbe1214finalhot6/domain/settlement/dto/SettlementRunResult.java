package com.back.nbe1214finalhot6.domain.settlement.dto;

import java.time.LocalDate;

/** 일 정산 한 번 실행한 결과. 새로 만든 건, 이미 있어서 넘긴 건, 실패한 건의 수를 담는다. */
public record SettlementRunResult(LocalDate date, int created, int skipped, int failed) {}
