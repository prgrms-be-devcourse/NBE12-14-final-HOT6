package com.back.nbe1214finalhot6.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.payment.PaymentTestFixture;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * T2(본처리) 단위 검증. Facade 를 거치지 않고 T1 로 PROCESSING 행을 직접 만든 뒤 tx() 를 호출한다.
 *
 * <p>주의: 클래스에 @Transactional 을 붙이지 않는다 (T2 의 커밋/롤백을 실제로 확인해야 하므로).
 */
@IntegrationTest
@Import(PaymentTestFixture.class)
class PaymentTxServiceTest {

  @Autowired PaymentTxService paymentTxService;
  @Autowired PaymentIdempotencyService paymentIdempotencyService; // T1: 행 만들기용
  @Autowired PaymentRepository paymentRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;
  @Autowired PaymentTestFixture fixture;
  @Autowired UserRepository userRepository;
  @Autowired MerchantRepository merchantRepository;
  @Autowired WalletService walletService;

  // QR 은 Mock: 대상 조회만 stub 하고, 사용 처리는 아무것도 안 하거나 예외를 던지게 한다
  @MockitoBean QrService qrService;
  // 캐시백은 실제 동작 그대로 두고, 롤백 테스트에서만 예외를 던지게 한다
  @MockitoSpyBean CashbackService cashbackService;

  // 가맹점·지갑(충전금 devMoney, 캐시백 cashback)·QR 을 준비하고 결제 요청을 만든다
  private PaymentRequest preparedRequest(long amount, long devMoney, long cashback, long cashbackUsed) {
    Long merchantId = fixture.saveMerchant();
    Long walletId = fixture.saveWallet(devMoney, cashback);
    String qrToken = fixture.stubQr(qrService, merchantId, amount);
    return new PaymentRequest(walletId, qrToken, cashbackUsed);
  }

  // 새 멱등키 (PaymentFacadeFailureTest 와 동일)
  private String newKey() {
    return UUID.randomUUID().toString();
  }

  // PROCESSING 상태의 결제 행을 만들고 그 id 를 돌려준다 (T1)
  private Long claimPayment(PaymentRequest request) {
    return paymentIdempotencyService.claim(newKey(), request).paymentId();
  }

  @Test
  @DisplayName("정상 처리 => COMPLETED, 사용분 합계 == 결제금액, 완료 시각 기록, 지갑 차감")
  void 정상처리() {
    // given : 충전금 7,000 + 캐시백 3,000 을 가진 지갑으로 10,000원 결제 (캐시백 3,000 사용)
    PaymentRequest request = preparedRequest(10_000L, 7_000L, 3_000L, 3_000L);
    Long paymentId = claimPayment(request);
    // when
    PaymentResponse response = paymentTxService.tx(paymentId);
    // then 1 : 응답 확인
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(response.walletUsedAmount()).isEqualTo(7_000L);
    assertThat(response.cashbackUsedAmount()).isEqualTo(3_000L);
    assertThat(response.walletUsedAmount() + response.cashbackUsedAmount()).isEqualTo(response.amount());
    // then 2 : DB 확인
    Payment saved = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(saved.getCompletedAt()).isNotNull();
    // then 3 : 지갑에서 두 칸이 모두 차감되었다
    var wallet = walletRepository.findById(request.walletId()).orElseThrow();
    assertThat(wallet.getBalance()).isZero();
    assertThat(wallet.getCashbackBalance()).isZero();
  }

  @Test
  @DisplayName("QR 처리 실패 => 예외 + 전체 롤백 (행은 PROCESSING 그대로, 지갑은 그대로)")
  void QR실패시롤백() {
    // given
    PaymentRequest request = preparedRequest(10_000L, 10_000L, 0L, 0L);
    Long paymentId = claimPayment(request);
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)).when(qrService).markUsed(any(), any());
    // when & then 1 : 예외 확인
    assertThatThrownBy(() -> paymentTxService.tx(paymentId)).isInstanceOf(BusinessException.class);
    // then 2 : 롤백 확인
    Payment saved = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    assertThat(walletRepository.findById(request.walletId()).orElseThrow().getBalance()).isEqualTo(10_000L);
  }

  @Test
  @DisplayName("지갑 차감 뒤 캐시백 처리가 실패하면 지갑 차감도 함께 롤백된다 (원장 기록 없음)")
  void 지갑차감후_캐시백실패시_롤백() {
    // given : 지갑 차감까지 성공한 뒤 reserve 가 예외를 던진다
    PaymentRequest request = preparedRequest(10_000L, 7_000L, 3_000L, 3_000L);
    Long paymentId = claimPayment(request);
    doThrow(new RuntimeException("캐시백 실패")).when(cashbackService).reserve(any());
    // when
    assertThatThrownBy(() -> paymentTxService.tx(paymentId)).isInstanceOf(RuntimeException.class);
    // then 1 : 결제 행은 PROCESSING 그대로
    Payment saved = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    // then 2 : 지갑 잔액이 처음 그대로 (차감이 롤백됨)
    var wallet = walletRepository.findById(request.walletId()).orElseThrow();
    assertThat(wallet.getBalance()).isEqualTo(7_000L);
    assertThat(wallet.getCashbackBalance()).isEqualTo(3_000L);
    // then 3 : 결제 차감 원장(음수 행)이 남지 않았다 (충전·적립 입금 행만 있음)
    long debitRows =
        walletTransactionRepository.findAll().stream()
            .filter(t -> t.getWalletId().equals(request.walletId()))
            .filter(t -> t.getAmount() < 0)
            .count();
    assertThat(debitRows).isZero();
  }

  @Test
  @DisplayName("본인 가맹점 결제 => 결제는 COMPLETED, 지갑은 차감되고 적립만 0원·SELF_PAYMENT (#301)")
  void 본인가맹점_결제는_완료되고_적립만_제외() {
    // given : 같은 사람이 같은 전화번호로 점주 계정과 이용자 계정을 따로 가진다
    String phone = String.format("010%08d", Math.floorMod(System.nanoTime(), 100_000_000L)); // 테스트마다 다른 번호
    String tag = UUID.randomUUID().toString().substring(0, 8);
    Long ownerId =
        userRepository.save(User.create("점주", "owner-" + tag + "@devpay.test", phone, "pw", "pin", UserRole.OWNER)).getId();
    Long userId =
        userRepository.save(User.create("이용자", "user-" + tag + "@devpay.test", phone, "pw", "pin", UserRole.USER)).getId();
    Long merchantId = merchantRepository.save(Merchant.create(ownerId, "본인가맹점", "인천", true)).getId();
    Long walletId = walletRepository.save(Wallet.create(userId, LocalDateTime.now())).getId();
    walletService.credit(walletId, 10_000L, WalletTransactionType.CHARGE, null);
    PaymentRequest request = new PaymentRequest(walletId, fixture.stubQr(qrService, merchantId, 10_000L), 0L);
    Long paymentId = claimPayment(request);
    // when
    PaymentResponse response = paymentTxService.tx(paymentId);
    // then : 결제는 정상 완료, 돈은 빠지고 적립만 제외된다
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(response.cashbackExclusionReason()).isEqualTo(CashbackExclusionReason.SELF_PAYMENT.name());
    assertThat(response.cashbackEarnedAmount()).isZero();
    assertThat(walletRepository.findById(walletId).orElseThrow().getBalance()).isZero();
  }

}
