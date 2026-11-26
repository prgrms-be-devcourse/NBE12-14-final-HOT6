package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutItemResult;

/** 지급 배치가 실행 중에 모으는 적립 한 건의 처리 결과. 실패가 아니면 오류 값은 null 이다. */
record PayoutItemLog(Long earningId, PayoutItemResult result, String errorCode, String errorMessage) {}
