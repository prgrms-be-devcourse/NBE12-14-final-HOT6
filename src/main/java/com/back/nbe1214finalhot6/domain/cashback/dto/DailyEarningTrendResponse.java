package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.util.List;

/** 일별 적립액 추이. 오래된 날짜부터 오늘까지다. */
public record DailyEarningTrendResponse(List<DailyEarningResponse> days) {}
