package com.back.nbe1214finalhot6.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.payment.PaymentTestFixture;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 결제 취소 서비스 검증.
 *
 * <p>주의: 클래스에 @Transactional 을 붙이지 않는다 (취소의 커밋/롤백을 실제로 확인해야 하므로).
 */
@IntegrationTest
@Import(PaymentTestFixture.class)
class PaymentCancelServiceTest {

  @Autowired PaymentCancelService paymentCancelService; // 테스트 대상
  @Autowired PaymentTxService paymentTxService; // 결제 완료 상태를 만들기 위한 T2
  @Autowired PaymentIdempotencyService paymentIdempotencyService; // T1
  @Autowired PaymentFailureService paymentFailureService; // T3: FAILED 상태 만들기용
  @Autowired PaymentRepository paymentRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;
  @Autowired PaymentTestFixture fixture;
  @Autowired MutableClock clock;
  @Autowired CashbackEarningRepository earningRepository;
  @Autowired JdbcTemplate jdbc;
  @Autowired CashbackBudgetService budgetService;

  @MockitoBean QrService qrService; // QR 은 stub
  @MockitoSpyBean CashbackService cashbackService; // 호출 검증(verify) + 롤백 테스트용 예외

  // ---- 준비 도구 ----

  // 아래 준비 메서드가 채운다. 충전금 7,000 + 캐시백 3,000 지갑으로 10,000원 결제 (캐시백 3,000 사용)
  private Long merchantId;
  private Long walletId;

  // T1 만 실행한 PROCESSING 상태의 결제 id
  private Long claimedPayment() {
    merchantId = fixture.saveMerchant();
    walletId = fixture.saveWallet(7_000L, 3_000L);
    String qrToken = fixture.stubQr(qrService, merchantId, 10_000L);
    return paymentIdempotencyService
        .claim(UUID.randomUUID().toString(), new PaymentRequest(walletId, qrToken, 3_000L))
        .paymentId();
  }

  // T1 + T2 까지 실행한 COMPLETED 상태의 결제 id
  private Long completedPayment() {
    Long paymentId = claimedPayment();
    paymentTxService.tx(paymentId);
    return paymentId;
  }

  private Wallet wallet() {
    return walletRepository.findById(walletId).orElseThrow();
  }

  private Payment payment(Long paymentId) {
    return paymentRepository.findById(paymentId).orElseThrow();
  }

  private static Object errorCodeOf(Throwable e) {
    return ((BusinessException) e).getErrorCode();
  }

  // ---- 테스트 ----

  @Test
  @DisplayName("정상 취소 => CANCELED, canceledAt 기록, completedAt 유지, 지갑 환급(데브머니·캐시백 각각)")
  void 정상취소() {
    // given
    Long paymentId = completedPayment();
    LocalDateTime completedBefore = payment(paymentId).getCompletedAt(); // 취소 전 값
    // when
    PaymentResponse response = paymentCancelService.cancel(merchantId, paymentId);
    // then 1 : 응답
    assertThat(response.status()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(response.canceledAt()).isNotNull();
    // then 2 : DB, 취소해도 completedAt 은 그대로
    Payment saved = payment(paymentId);
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(saved.getCanceledAt()).isNotNull();
    assertThat(saved.getCompletedAt()).isEqualTo(completedBefore);
    // then 3 : 지갑 복구 (결제 전 7,000 / 3,000)
    assertThat(wallet().getBalance()).isEqualTo(7_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(3_000L);
    // then 4 : 환급 원장 기록
    assertThat(walletTransactionRepository.findByTypeAndRefKey(
            WalletTransactionType.PAYMENT_CANCEL_DEVMONEY, paymentId)).isPresent();
    assertThat(walletTransactionRepository.findByTypeAndRefKey(
            WalletTransactionType.PAYMENT_CANCEL_CASHBACK, paymentId)).isPresent();
  }

  @Test
  @DisplayName("취소 시각 => Clock(KST) 기준, 결제일과 취소일이 서로 다른 날짜로 기록된다")
  void 취소시각_날짜경계() {
    // given : 11/20 23:59:58 KST (= 14:59:58 UTC) 에 결제 완료
    clock.set(Instant.parse("2026-11-20T14:59:58Z"));
    Long paymentId = completedPayment();
    // when : 11/21 00:00:02 KST 로 옮긴 뒤 취소
    clock.set(Instant.parse("2026-11-20T15:00:02Z"));
    paymentCancelService.cancel(merchantId, paymentId);
    // then : 결제는 완료한 날, 취소는 취소한 날로 집계된다
    Payment saved = payment(paymentId);
    assertThat(saved.getCompletedAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(saved.getCanceledAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 21));
    assertThat(saved.getCanceledAt()).isEqualTo(LocalDateTime.of(2026, 11, 21, 0, 0, 2));
  }

  @Test
  @DisplayName("중복 취소 => 에러 없이 기존 결과 반환, 환급은 한 번만 일어난다")
  void 중복취소() {
    // given : 정상 취소를 한 번 한 상태
    Long paymentId = completedPayment();
    PaymentResponse first = paymentCancelService.cancel(merchantId, paymentId);
    // when : 시간이 지난 뒤 같은 결제를 한 번 더 취소
    clock.set(Instant.parse("2026-11-19T00:00:00Z"));
    PaymentResponse[] second = new PaymentResponse[1];
    assertThatCode(() -> second[0] = paymentCancelService.cancel(merchantId, paymentId))
        .doesNotThrowAnyException();
    // then 1 : 상태는 CANCELED, 취소 시각은 첫 취소 그대로
    assertThat(second[0].status()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(second[0].canceledAt()).isEqualTo(first.canceledAt());
    // then 2 : 지갑이 두 번 늘지 않음
    assertThat(wallet().getBalance()).isEqualTo(7_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(3_000L);
  }

  @Test
  @DisplayName("남의 가맹점 취소 => PAY-007, 아무것도 바뀌지 않는다")
  void 남의가맹점_취소거절() {
    // given
    Long paymentId = completedPayment();
    Long otherMerchantId = fixture.saveMerchant();
    // when & then 1 : 예외
    assertThatThrownBy(() -> paymentCancelService.cancel(otherMerchantId, paymentId))
        .isInstanceOf(BusinessException.class)
        .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_MERCHANT_MISMATCH));
    // then 2 : 결제는 COMPLETED 그대로, 환급 없음
    assertThat(payment(paymentId).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment(paymentId).getCanceledAt()).isNull();
    assertThat(wallet().getBalance()).isZero();
    assertThat(wallet().getCashbackBalance()).isZero();
  }

  @Test
  @DisplayName("없는 결제 취소 => PAY-003")
  void 없는결제() {
    assertThatThrownBy(() -> paymentCancelService.cancel(1L, 999_999_999L))
        .isInstanceOf(BusinessException.class)
        .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
  }

  @Test
  @DisplayName("PROCESSING 건 취소 거절 => PAY-006, 상태 그대로")
  void PROCESSING_취소거절() {
    // given : T1 만 실행한 행
    Long paymentId = claimedPayment();
    // when & then
    assertThatThrownBy(() -> paymentCancelService.cancel(merchantId, paymentId))
        .isInstanceOf(BusinessException.class)
        .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_NOT_CANCELABLE));
    assertThat(payment(paymentId).getStatus()).isEqualTo(PaymentStatus.PROCESSING);
  }

  @Test
  @DisplayName("FAILED 건 취소 거절 => PAY-006, 상태 그대로")
  void FAILED_취소거절() {
    // given : T1 후 T3 로 FAILED 처리
    Long paymentId = claimedPayment();
    paymentFailureService.failure(paymentId, "WAL-004");
    // when & then
    assertThatThrownBy(() -> paymentCancelService.cancel(merchantId, paymentId))
        .isInstanceOf(BusinessException.class)
        .satisfies(e -> assertThat(errorCodeOf(e)).isEqualTo(PaymentErrorCode.PAYMENT_NOT_CANCELABLE));
    assertThat(payment(paymentId).getStatus()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  @DisplayName("취소 중 캐시백 취소가 실패하면 지갑 환급도 함께 롤백된다")
  void 롤백() {
    // given : 결제 완료 후, 캐시백 취소가 예외를 던지게 한다
    Long paymentId = completedPayment();
    doThrow(new RuntimeException("캐시백 취소 실패")).when(cashbackService).cancel(any());
    // when & then 1 : 예외
    assertThatThrownBy(() -> paymentCancelService.cancel(merchantId, paymentId))
        .isInstanceOf(RuntimeException.class);
    // then 2 : 상태는 COMPLETED 그대로, 지갑은 결제 직후 값(0 / 0) 그대로
    assertThat(payment(paymentId).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment(paymentId).getCanceledAt()).isNull();
    assertThat(wallet().getBalance()).isZero();
    assertThat(wallet().getCashbackBalance()).isZero();
    // then 3 : 환급 원장도 남지 않는다
    assertThat(walletTransactionRepository.findByTypeAndRefKey(
            WalletTransactionType.PAYMENT_CANCEL_DEVMONEY, paymentId)).isEmpty();
  }

  @Test
  @DisplayName("취소하면 cashbackService.cancel(paymentId) 가 호출된다")
  void 캐시백취소_호출() {
    // given
    Long paymentId = completedPayment();
    // when
    paymentCancelService.cancel(merchantId, paymentId);
    // then
    verify(cashbackService).cancel(paymentId);
  }

  @Test
  @DisplayName("거절된 취소는 캐시백 취소를 호출하지 않는다")
  void 거절되면_캐시백취소_미호출() {
    // given : 남의 가맹점이 취소 시도
    Long paymentId = completedPayment();
    Long otherMerchantId = fixture.saveMerchant();
    // when
    assertThatThrownBy(() -> paymentCancelService.cancel(otherMerchantId, paymentId))
        .isInstanceOf(BusinessException.class);
    // then
    verify(cashbackService, never()).cancel(any());
  }

  // ---- 해지 진행·완료 가맹점의 취소 거절 (#289) ----

  private void changeMerchantStatus(MerchantStatus status) {
    jdbc.update("update merchant set status = ? where id = ?", status.name(), merchantId);
  }

  // 거절된 취소는 결제·지갑·원장·적립 어느 것도 바꾸지 않는다
  private void assertNothingMoved(Long paymentId) {
    assertThat(payment(paymentId).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment(paymentId).getCanceledAt()).isNull();
    assertThat(wallet().getBalance()).isEqualTo(0L); // 결제 뒤 잔액 그대로 (7,000 - 7,000)
    assertThat(wallet().getCashbackBalance()).isEqualTo(0L); // (3,000 - 3,000)
    assertThat(walletTransactionRepository.findByTypeAndRefKey(
            WalletTransactionType.PAYMENT_CANCEL_DEVMONEY, paymentId)).isEmpty();
    assertThat(walletTransactionRepository.findByTypeAndRefKey(
            WalletTransactionType.PAYMENT_CANCEL_CASHBACK, paymentId)).isEmpty();
    verify(cashbackService, never()).cancel(any());
  }

  @Test
  @DisplayName("해지 진행 중(CLOSING) 가맹점의 취소 => MER-003, 돈이 움직이지 않는다")
  void 해지진행중_취소거절() {
    // given : 결제 완료 뒤 점주가 해지를 신청함
    Long paymentId = completedPayment();
    changeMerchantStatus(MerchantStatus.CLOSING);
    clearInvocations(cashbackService);
    // when & then
    assertThatThrownBy(() -> paymentCancelService.cancel(merchantId, paymentId))
        .isInstanceOf(BusinessException.class)
        .extracting(PaymentCancelServiceTest::errorCodeOf)
        .isEqualTo(MerchantErrorCode.MERCHANT_CLOSING);
    assertNothingMoved(paymentId);
  }

  @Test
  @DisplayName("해지 완료(CLOSED) 가맹점의 취소 => MER-004, 돈이 움직이지 않는다")
  void 해지완료_취소거절() {
    // given
    Long paymentId = completedPayment();
    changeMerchantStatus(MerchantStatus.CLOSED);
    clearInvocations(cashbackService);
    // when & then
    assertThatThrownBy(() -> paymentCancelService.cancel(merchantId, paymentId))
        .isInstanceOf(BusinessException.class)
        .extracting(PaymentCancelServiceTest::errorCodeOf)
        .isEqualTo(MerchantErrorCode.MERCHANT_CLOSED);
    assertNothingMoved(paymentId);
  }

  @Test
  @DisplayName("해지 전에 취소한 결제를 해지 뒤 다시 취소 => 에러 없이 기존 취소 결과 반환")
  void 해지전_취소건_재요청() {
    // given : 영업 중에 취소한 뒤 해지 완료
    Long paymentId = completedPayment();
    paymentCancelService.cancel(merchantId, paymentId);
    LocalDateTime canceledAt = payment(paymentId).getCanceledAt(); // DB 에 저장된 값
    changeMerchantStatus(MerchantStatus.CLOSED);
    // when
    PaymentResponse again = paymentCancelService.cancel(merchantId, paymentId);
    // then : 같은 결과, 환급은 한 번뿐 (결제 전 잔액으로 복구된 그대로)
    assertThat(again.status()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(again.canceledAt()).isEqualTo(canceledAt);
    assertThat(wallet().getBalance()).isEqualTo(7_000L);
    assertThat(wallet().getCashbackBalance()).isEqualTo(3_000L);
  }

  @Test
  @DisplayName("취소는 QR 을 건드리지 않는다 (QR 은 사용됨 상태 그대로)")
  void 취소는_QR을_건드리지_않는다() {
    // given : 결제까지 끝낸 뒤 QR 호출 기록을 비운다
    Long paymentId = completedPayment();
    clearInvocations(qrService);
    // when
    paymentCancelService.cancel(merchantId, paymentId);
    // then
    verify(qrService, never()).markUsed(any(), any());
  }

  @Test
  @DisplayName("지급 후 오래된 결제도 취소할 수 있다 (정산·지급 여부로 거절하지 않는다)")
  void 오래된결제_취소가능() {
    // given : 한 달 전에 결제 완료
    clock.set(Instant.parse("2026-10-18T00:00:00Z"));
    Long paymentId = completedPayment();
    // when : 한 달 뒤에 취소
    clock.set(Instant.parse("2026-11-18T00:00:00Z"));
    PaymentResponse response = paymentCancelService.cancel(merchantId, paymentId);
    // then : 성공, 취소 시각은 취소한 날, 완료 시각은 한 달 전 그대로
    assertThat(response.status()).isEqualTo(PaymentStatus.CANCELED);
    Payment saved = payment(paymentId);
    assertThat(saved.getCompletedAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 18));
    assertThat(saved.getCanceledAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 18));
  }

  @Test
  @DisplayName("캐시백이 이미 지급된 결제를 취소하면 지급된 캐시백이 회수된다")
  void 지급된_캐시백_회수() {
    // given : 예산이 있어서 적립 예정(PENDING, 데브머니 7,000 x 3% = 210원)이 생기는 결제를 완료하고, 지급(PAID)된 상태로 만든다
    budgetService.putAnnual(2026, new AnnualBudgetRequest(10_000_000L));
    budgetService.putMonthly(
        2026, 11, new MonthlyBudgetRequest(5_000_000L, new BigDecimal("0.03")));
    Long paymentId = completedPayment();
    CashbackEarning earning = earningRepository.findByPaymentId(paymentId).orElseThrow();
    assertThat(earning.getAmount()).isEqualTo(210L);
    jdbc.update(
        "update cashback_earning set status = 'PAID', paid_at = now() where id = ?",
        earning.getId());
    // when : 지급 후에 결제를 취소해도 예외 없이 취소된다
    PaymentResponse response = paymentCancelService.cancel(merchantId, paymentId);
    // then 1 : 결제는 CANCELED, 사용한 데브머니·캐시백이 환급된다
    assertThat(response.status()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(wallet().getBalance()).isEqualTo(7_000L);
    // then 2 : 지급받은 캐시백 210원은 회수되어, 환급된 캐시백 3,000원에서 빠진다
    assertThat(wallet().getCashbackBalance()).isEqualTo(3_000L - 210L);
    assertThat(
            walletTransactionRepository.findByTypeAndRefKey(
                WalletTransactionType.CASHBACK_CLAWBACK, earning.getId()))
        .isPresent();
    // then 3 : 적립 건은 회수 상태가 된다
    assertThat(earningRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
        .isEqualTo(EarningStatus.CLAWED_BACK);
    verify(cashbackService).cancel(paymentId);
  }
}
