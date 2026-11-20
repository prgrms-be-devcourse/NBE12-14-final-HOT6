package com.back.nbe1214finalhot6.domain.payment.mock;

import org.springframework.stereotype.Service;

// TODO: C의 WalletService 연동 후 삭제 (PaymentTxService 의 주입 타입을 WalletService 로 교체)
@Service
public class WalletMockService {

  /**
   * 지갑 락을 잡고 차감한다. 데브머니/캐시백 사용분을 돌려준다. (실제 구현은 잔액 부족이면 BusinessException)
   * 호출한 쪽(T2) 트랜잭션에 참여한다(REQUIRED) → T2 롤백 시 함께 롤백된다.
   *
   * @param type 거래 유형 (결제는 PAYMENT)
   * @param refKey 원장 참조 키. 결제는 paymentId. 같은 (type, refKey)가 다시 오면 중복 반영하지 않는다
   * @param allowNegative 캐시백 잔액이 음수가 되는 것을 허용할지 (결제는 false, 회수는 true)
   */
  public DebitResult debit(
      Long walletId, long amount, WalletTxType type, Long refKey, boolean allowNegative) {
    // 가짜 구현: 전부 데브머니로 차감했다고 가정
    return new DebitResult(amount, 0L);
  }

  public record DebitResult(long walletUsed, long cashbackUsed) {}
}
