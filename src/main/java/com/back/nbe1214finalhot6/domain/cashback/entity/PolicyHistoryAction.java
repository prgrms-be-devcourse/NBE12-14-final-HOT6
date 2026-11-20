package com.back.nbe1214finalhot6.domain.cashback.entity;

/** 정책(요율 구간) 변경 종류. */
public enum PolicyHistoryAction {
  /** 구간을 만들었다. */
  CREATE,
  /** 기존 구간의 값(종료 시각 등)을 바꿨다. */
  UPDATE
}
