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
  STOP_CANCELED,
  /** 한도 변경. 운영자가 한도를 바꾸거나 예약하면서 알릴 때 등록한다. */
  LIMIT_CHANGE;

  /** 제목이 없는 공지(제목이 생기기 전에 등록된 것)와 자동 공지에 쓰는 기본 제목. */
  public String defaultTitle() {
    return switch (this) {
      case POLICY_CHANGE -> "적립률 변경 안내";
      case BUDGET_NOTICE -> "적립 중단 예정 안내";
      case EMERGENCY_STOP -> "적립 중단 안내";
      case STOP_CANCELED -> "중단 예정 취소 안내";
      case LIMIT_CHANGE -> "한도 변경 안내";
    };
  }
}
