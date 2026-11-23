package com.back.nbe1214finalhot6.domain.payment;

import static org.mockito.BDDMockito.given;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.qr.dto.QrPaymentTarget;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

/**
 * 결제 통합 테스트용 준비 도구. 가맹점, 지갑(잔액 포함), QR 대상 조회를 한 줄로 준비한다.
 *
 * <p>사용하는 테스트 클래스에 {@code @Import(PaymentTestFixture.class)} 를 붙인다.
 */
@TestComponent
@RequiredArgsConstructor
public class PaymentTestFixture {

  // 지갑의 user_id 는 유니크이므로 테스트마다 겹치지 않는 값을 쓴다
  private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis());

  private final MerchantRepository merchantRepository;
  private final WalletRepository walletRepository;
  private final WalletService walletService;
  private final Clock clock;

  public Long saveMerchant() {
    return merchantRepository.save(Merchant.create(null, "테스트가맹점", "인천", true)).getId();
  }

  /** 데브머니와 캐시백 잔액을 가진 지갑을 만들고 지갑 id 를 돌려준다. */
  public Long saveWallet(long devMoney, long cashback) {
    Wallet wallet =
        walletRepository.save(Wallet.create(SEQ.incrementAndGet(), LocalDateTime.now(clock)));
    Long walletId = wallet.getId();
    if (devMoney > 0) {
      walletService.credit(walletId, devMoney, WalletTransactionType.CHARGE, null);
    }
    if (cashback > 0) {
      walletService.credit(
          walletId, cashback, WalletTransactionType.CASHBACK_EARN, SEQ.incrementAndGet());
    }
    return walletId;
  }

  /** QR 서비스(Mock)가 이 토큰으로 가맹점·금액을 돌려주게 하고 토큰을 돌려준다. */
  public String stubQr(QrService qrService, Long merchantId, long amount) {
    String token = UUID.randomUUID().toString();
    given(qrService.getPaymentTarget(token)).willReturn(new QrPaymentTarget(merchantId, amount));
    return token;
  }
}
