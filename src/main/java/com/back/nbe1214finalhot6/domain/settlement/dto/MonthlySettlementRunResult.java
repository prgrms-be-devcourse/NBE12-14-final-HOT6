package com.back.nbe1214finalhot6.domain.settlement.dto;

import java.time.YearMonth;

/** 월 정산 한 번 실행한 결과. 새로 만든 건, 이번에 지급을 끝낸 건, 실패한 건의 수를 담는다. */
public record MonthlySettlementRunResult(YearMonth month, int created, int paid, int failed) {}
