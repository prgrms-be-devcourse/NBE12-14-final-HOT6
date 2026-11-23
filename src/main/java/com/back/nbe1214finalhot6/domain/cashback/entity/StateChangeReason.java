package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 월 예산의 적립 상태가 바뀐 이유. 전이 이력에 남긴다. */
public enum StateChangeReason {
  /** 예산 확보 직후 남은 예산이 공지선(대비금) 이하가 되어 공지 상태로 바뀌었다. */
  AUTO_NOTICE_LINE,
  /** 예산 확보 직후 남은 예산이 긴급선(대비금 × 긴급선 비율) 이하가 되어 즉시 중단했다. */
  AUTO_EMERGENCY_LINE,
  /** 공지 후 중단 예약 시각이 되어 중단했다. */
  SCHEDULED_TIME,
  /** 운영자가 "공지 후 중단"을 눌러 공지 상태로 바꿨다. */
  OPERATOR_NOTICE_STOP,
  /** 운영자가 "긴급 중단"을 눌러 즉시 중단했다. */
  OPERATOR_EMERGENCY_STOP
}
