package com.back.nbe1214finalhot6.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.payment.PaymentTestFixture;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.qr.exception.QrErrorCode;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
@Import(PaymentTestFixture.class)
class PaymentFacadeTest {

  @Autowired PaymentFacade paymentFacade;
  @Autowired PaymentRepository paymentRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired MutableClock clock;
  @Autowired PaymentTestFixture fixture;

  // QR 은 Mock: 대상 조회만 stub 하고, 사용 처리는 아무것도 하지 않는다 (지갑은 실제 서비스)
  @MockitoBean QrService qrService;

  // 가맹점·지갑(충전금 devMoney, 캐시백 cashback)·QR 을 준비하고 결제 요청을 만든다
  private PaymentRequest preparedRequest(long amount, long devMoney, long cashback, long cashbackUsed) {
    Long merchantId = fixture.saveMerchant();
    Long walletId = fixture.saveWallet(devMoney, cashback);
    String qrToken = fixture.stubQr(qrService, merchantId, amount);
    return new PaymentRequest(walletId, qrToken, cashbackUsed);
  }

  // 캐시백을 쓰지 않는 10,000원 결제 (충전금 전액)
  private PaymentRequest preparedRequest() {
    return preparedRequest(10_000L, 10_000L, 0L, 0L);
  }

  /*
    1. 새로운 멱등키 요청 => '진행중' 처리 => '완료' 처리, 충전금·캐시백 사용분 저장과 지갑 차감
    2. 캐시백 사용분이 결제 금액보다 크면 => 에러, 결제 행 없음
    3. 같은 멱등키, 다른(큐알, 캐시백 사용분)요청 => 에러
    4. 같은 멱등키, 같은 요청, '진행중'상태 => 에러
    5. 같은 멱등키, 같은 요청, '완료'상태 => 기존결과 반환, 행 1개, 상태유지('완료')
    6. 같은 멱등키, 같은 요청, '실패'상태 => 재시도 없이 기존 실패 결과 반환
   */
  @Test
  @DisplayName("새로운 멱등키 요청 => '완료' 처리, 사용분 저장, 지갑 차감")
  void 새로운멱등키요청() {
    // given : 캐시백을 쓰지 않으면 결제금액 전부를 데브머니로 낸다
    PaymentRequest request = preparedRequest();
    String key = UUID.randomUUID().toString();
    // when
    PaymentResponse response = paymentFacade.pay(key, request);
    // then 1 : 응답확인
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    // then 2 : DB확인 (가맹점·금액은 QR 에서 읽어 저장, 사용분은 T1 에서 계산해 저장)
    Payment saved =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(saved.getAmount()).isEqualTo(10_000L);
    assertThat(saved.getWalletUsedAmount()).isEqualTo(10_000L);
    assertThat(saved.getCashbackUsedAmount()).isZero();
    // then 3 : 지갑 데브머니가 차감되었다
    assertThat(walletRepository.findById(request.walletId()).orElseThrow().getBalance()).isZero();
  }

  @Test
  @DisplayName("캐시백 사용분이 있으면 충전금 사용분 = 금액 - 캐시백 사용분 으로 저장하고 두 칸을 차감한다")
  void 캐시백사용분_저장() {
    // given : 10,000원 결제, 캐시백 3,000 사용 => 충전금 7,000
    PaymentRequest request = preparedRequest(10_000L, 7_000L, 3_000L, 3_000L);
    String key = UUID.randomUUID().toString();
    // when
    PaymentResponse response = paymentFacade.pay(key, request);
    // then
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    Payment saved =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(saved.getWalletUsedAmount()).isEqualTo(7_000L);
    assertThat(saved.getCashbackUsedAmount()).isEqualTo(3_000L);
    var wallet = walletRepository.findById(request.walletId()).orElseThrow();
    assertThat(wallet.getBalance()).isZero();
    assertThat(wallet.getCashbackBalance()).isZero();
  }

  @Test
  @DisplayName("캐시백 사용분이 결제 금액보다 크면 COM-001 로 거절하고 결제 행은 만들어지지 않는다")
  void 캐시백사용분이_금액초과() {
    // given : 10,000원 결제에 10,001원 캐시백
    PaymentRequest request = preparedRequest(10_000L, 10_000L, 20_000L, 10_001L);
    String key = UUID.randomUUID().toString();
    // when & then
    assertThatThrownBy(() -> paymentFacade.pay(key, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    assertThat(paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key)).isEmpty();
  }

  @Test
  @DisplayName("결제 완료 시 접수 시각·완료 시각이 Clock(KST) 기준으로 저장된다")
  void 완료시각검증() {
    // given : 시계를 2026-11-18 10:30 KST (= 01:30 UTC) 로 고정
    clock.set(Instant.parse("2026-11-18T01:30:00Z"));
    PaymentRequest request = preparedRequest();
    String key = UUID.randomUUID().toString();
    // when
    PaymentResponse response = paymentFacade.pay(key, request);
    // then
    Payment saved = paymentRepository.findById(response.paymentId()).orElseThrow();
    LocalDateTime expected = LocalDateTime.of(2026, 11, 18, 10, 30);
    assertThat(saved.getRequestedAt()).isEqualTo(expected);
    assertThat(saved.getCompletedAt()).isEqualTo(expected);
  }

  @Test
  @DisplayName("같은 멱등키, 다른 큐알 요청 => 에러")
  void 같은멱등키_다른큐알() {
    // given : 같은 지갑으로 서로 다른 QR 두 개
    Long merchantId = fixture.saveMerchant();
    Long walletId = fixture.saveWallet(30_000L, 0L); // walletId 는 같아야 함 ((walletId, 멱등키)가 유니크라서 지갑이 다르면 새 요청이 됨)
    String key = UUID.randomUUID().toString();
    PaymentRequest pastRequest =
        new PaymentRequest(walletId, fixture.stubQr(qrService, merchantId, 10_000L), 0L);
    PaymentRequest currentRequest =
        new PaymentRequest(walletId, fixture.stubQr(qrService, merchantId, 20_000L), 0L);
    paymentFacade.pay(key, pastRequest); // 먼저 최초 요청으로 기존 행을 만들어 둠
    // when & then
    assertThatThrownBy(() -> paymentFacade.pay(key, currentRequest))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);
  }

  @Test
  @DisplayName("같은 멱등키, 같은 큐알인데 캐시백 사용분만 다른 요청 => 에러")
  void 같은멱등키_캐시백사용분만다름() {
    // given
    Long merchantId = fixture.saveMerchant();
    Long walletId = fixture.saveWallet(10_000L, 0L);
    String qrToken = fixture.stubQr(qrService, merchantId, 10_000L);
    String key = UUID.randomUUID().toString();
    paymentFacade.pay(key, new PaymentRequest(walletId, qrToken, 0L)); // 최초 요청
    // when & then : 같은 키로 캐시백 사용분만 바꿔 보낸다
    assertThatThrownBy(() -> paymentFacade.pay(key, new PaymentRequest(walletId, qrToken, 1_000L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);
  }

  @Test
  @DisplayName("같은 멱등키, 같은 요청, '진행중'상태 => 에러")
  void 같은멱등키같은요청인데진행중상태() {
    // given
    PaymentRequest request = preparedRequest();
    String key = UUID.randomUUID().toString();
    PaymentResponse response = paymentFacade.pay(key, request); // 먼저 최초 요청으로 기존 행을 만들어 둠
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    paymentRepository.updateStatusIfCurrent(
        response.paymentId(), PaymentStatus.COMPLETED, PaymentStatus.PROCESSING);
    // when & then
    assertThatThrownBy(() -> paymentFacade.pay(key, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
  }

  @Test
  @DisplayName("같은 멱등키, 같은 요청, '완료'상태 => 기존결과 반환, 행 1개, 상태유지('완료')")
  void 같은멱등키같은요청인데완료상태() {
    // given
    PaymentRequest request = preparedRequest();
    String key = UUID.randomUUID().toString();
    // when
    PaymentResponse pastResponse = paymentFacade.pay(key, request); // 기존요청
    PaymentResponse currentResponse = paymentFacade.pay(key, request);
    // then : 새로 처리하지 않고 같은 결과, 지갑도 한 번만 차감
    assertThat(currentResponse.paymentId()).isEqualTo(pastResponse.paymentId());
    assertThat(currentResponse.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(walletRepository.findById(request.walletId()).orElseThrow().getBalance()).isZero();
  }

  @Test
  @DisplayName("같은 멱등키 재시도는 QR·가맹점 상태가 바뀌어도(해지 신청 등) 저장된 '완료' 결과를 반환한다 (#252)")
  void 같은멱등키_QR상태가_바뀌어도_저장된결과반환() {
    // given : 결제가 완료된 뒤 QR 조회가 오류를 던지게 바꾼다 (해지 신청으로 가맹점이 결제를 받지 않는 상황)
    PaymentRequest request = preparedRequest();
    String key = UUID.randomUUID().toString();
    PaymentResponse past = paymentFacade.pay(key, request);
    given(qrService.getPaymentTarget(request.qrToken()))
        .willThrow(new BusinessException(QrErrorCode.QR_NOT_FOUND));
    // when : 같은 키로 재시도
    PaymentResponse retried = paymentFacade.pay(key, request);
    // then : 오류가 아니라 처음 결과를 그대로 받고, 새로 처리하지 않는다
    assertThat(retried.paymentId()).isEqualTo(past.paymentId());
    assertThat(retried.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(walletRepository.findById(request.walletId()).orElseThrow().getBalance()).isZero();
  }

  @Test
  @DisplayName("같은 멱등키, '진행중'인 요청은 QR 상태와 무관하게 IDEMPOTENCY_IN_PROGRESS 이다 (#252)")
  void 같은멱등키_진행중이면_QR상태와_무관하게_진행중오류() {
    // given
    PaymentRequest request = preparedRequest();
    String key = UUID.randomUUID().toString();
    PaymentResponse response = paymentFacade.pay(key, request);
    paymentRepository.updateStatusIfCurrent(
        response.paymentId(), PaymentStatus.COMPLETED, PaymentStatus.PROCESSING);
    given(qrService.getPaymentTarget(request.qrToken()))
        .willThrow(new BusinessException(QrErrorCode.QR_NOT_FOUND));
    // when & then : QR 오류가 아니라 진행 중 오류
    assertThatThrownBy(() -> paymentFacade.pay(key, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
  }

  @Test
  @DisplayName("새 멱등키 요청은 QR·가맹점 상태 검증을 그대로 거친다. 오류면 결제 행이 만들어지지 않는다 (#252)")
  void 새멱등키는_QR검증을_그대로() {
    // given : QR 조회가 오류를 던진다
    Long walletId = fixture.saveWallet(10_000L, 0L);
    String qrToken = UUID.randomUUID().toString();
    given(qrService.getPaymentTarget(qrToken)).willThrow(new BusinessException(QrErrorCode.QR_NOT_FOUND));
    String key = UUID.randomUUID().toString();
    // when & then
    assertThatThrownBy(() -> paymentFacade.pay(key, new PaymentRequest(walletId, qrToken, 0L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(QrErrorCode.QR_NOT_FOUND);
    assertThat(paymentRepository.findByWalletIdAndIdempotencyKey(walletId, key)).isEmpty();
  }

  @Test
  @DisplayName("같은 멱등키, 같은 요청, '실패'상태 => 재시도 없이 기존 실패 결과 반환")
  void 같은멱등키같은요청인데실패상태() {
    // given
    PaymentRequest request = preparedRequest();
    String key = UUID.randomUUID().toString();
    PaymentResponse pastResponse = paymentFacade.pay(key, request); // 기존요청 (COMPLETED)
    // 기존요청을 실패 상태로 바꿔 둔다
    paymentRepository.updateStatusIfCurrent(
        pastResponse.paymentId(), PaymentStatus.COMPLETED, PaymentStatus.FAILED);
    // when
    PaymentResponse currentResponse = paymentFacade.pay(key, request);
    // then : 새로 처리하지 않고 기존 행의 실패 결과를 그대로 돌려준다
    assertThat(currentResponse.paymentId()).isEqualTo(pastResponse.paymentId());
    assertThat(currentResponse.status()).isEqualTo(PaymentStatus.FAILED);
  }
}
