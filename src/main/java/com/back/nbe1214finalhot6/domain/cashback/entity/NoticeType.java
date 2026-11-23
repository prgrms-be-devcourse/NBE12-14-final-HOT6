package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 이용자에게 보이는 캐시백 공지의 종류. */
public enum NoticeType {
  /** 정책 변경 예고. 요율을 인하하면 등록한다. */
  POLICY_CHANGE,
  /** 소진 예고. 적립이 곧 중단될 수 있다는 공지로, 공지 상태로 바뀔 때 등록한다. */
  BUDGET_NOTICE,
  /** 긴급 중단. 적립이 즉시 중단됐을 때 등록한다. */
  EMERGENCY_STOP,
  /** 중단 예정 취소. 공지했던 중단이 취소됐을 때 등록한다. 되돌리는 전이가 생기면 연결한다. */
  STOP_CANCELED
}
