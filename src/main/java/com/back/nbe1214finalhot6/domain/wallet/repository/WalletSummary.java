package com.back.nbe1214finalhot6.domain.wallet.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.WalletStatus;

/** 지갑 조회 화면에 필요한 값만 읽는 투영. Wallet 엔티티가 아니라 컬럼 값이므로 영속성 컨텍스트에 남지 않음. */
public interface WalletSummary {

  Long getId();

  // 데브머니 잔액
  long getBalance();

  // 캐시백 잔액. 회수로 음수가 될 수 있음
  long getCashbackBalance();

  WalletStatus getStatus();
}
