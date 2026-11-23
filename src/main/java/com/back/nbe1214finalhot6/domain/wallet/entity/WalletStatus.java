package com.back.nbe1214finalhot6.domain.wallet.entity;

/** 지갑 상태. 탈퇴한 회원의 지갑은 입금·차감을 거절함. */
public enum WalletStatus {
  ACTIVE,
  WITHDRAWN
}
