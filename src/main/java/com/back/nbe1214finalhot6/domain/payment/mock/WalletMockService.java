package com.back.nbe1214finalhot6.domain.payment.mock;

import org.springframework.stereotype.Service;

// TODO: C의 WalletService 연동 후 삭제 (PaymentTxService 의 주입 타입을 WalletService 로 교체)
@Service
public class WalletMockService {

  // 지갑 락을 잡고 차감. 데브머니/캐시백 사용분을 돌려줌. (실제 구현은 잔액 부족이면 BusinessException)
  public DebitResult debit(Long walletId, long amount) {
    // 가짜 구현: 전부 데브머니로 차감했다고 가정
    return new DebitResult(amount, 0L);
  }

  public record DebitResult(long walletUsed, long cashbackUsed) {}
}
