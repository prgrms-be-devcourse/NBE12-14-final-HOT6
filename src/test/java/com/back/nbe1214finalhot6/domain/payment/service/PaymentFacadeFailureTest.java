package com.back.nbe1214finalhot6.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.payment.PaymentTestFixture;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** T3(실패 처리) 통합 테스트. T2 에서 예외가 나면 T1 이 만든 행이 FAILED + failure_code 로 남는지 검증한다. */
@IntegrationTest
@Import(PaymentTestFixture.class)
class PaymentFacadeFailureTest {

  @Autowired PaymentFacade paymentFacade;
  @Autowired PaymentIdempotencyService paymentIdempotencyService; // T1: 행 만들기용
  @Autowired PaymentRepository paymentRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired PaymentTestFixture fixture;
  @Autowired private PaymentTxService paymentTxService;

  // 실패 상황을 만들기 위해 QR 서비스를 Mock 으로 교체한다 (지갑은 실제 서비스를 사용)
  @MockitoBean QrService qrService;
  // 실제 동작은 그대로 두고, 필요한 테스트에서만 예외를 던지게 하거나 호출 여부를 확인한다 (Spy)
  @MockitoSpyBean private PaymentFailureService paymentFailureService;

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

  // 새 멱등키
  private String newKey() {
    return UUID.randomUUID().toString();
  }

  @Test
  @DisplayName("T2 가 BusinessException 으로 실패하면 FAILED + 해당 에러 코드가 기록되고 예외가 전파된다")
  void T2_비즈니스예외_실패기록() {
    // given
    String key = newKey();
    PaymentRequest request = preparedRequest();
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)).when(qrService).markUsed(any(), any());
    // when
    assertThatThrownBy(() -> paymentFacade.pay(key, request)).isInstanceOf(BusinessException.class);
    // then
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.getCode());
  }

  @Test
  @DisplayName("T2 가 일반 RuntimeException 으로 실패하면 FAILED + COM-010 이 기록된다")
  void T2_일반예외_실패기록() {
    // given
    String key = newKey();
    PaymentRequest request = preparedRequest();
    doThrow(new RuntimeException()).when(qrService).markUsed(any(), any());
    // when
    // assertThatThrownBy : 예외를 받아내서 실제예외가 났다는 결과값만 세팅하는 역할
    // isInstanceOf : 예외종류확인용
    assertThatThrownBy(() -> paymentFacade.pay(key, request)).isInstanceOf(RuntimeException.class);
    // then
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.getCode());
  }

  @Test
  @DisplayName("T2 가 IDEMPOTENCY_IN_PROGRESS 를 던지면 FAILED 로 바꾸지 않고 PROCESSING 을 유지한다")
  void T2_멱등키처리중예외_실패기록안함() {
    // given
    String key = newKey();
    PaymentRequest request = preparedRequest();
    doThrow(new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS)).when(qrService).markUsed(any(), any());
    // when
    assertThatThrownBy(() -> paymentFacade.pay(key, request)).isInstanceOf(BusinessException.class);
    // then
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    assertThat(payment.getFailureCode()).isNull();
  }

  @Test
  @DisplayName("T2 가 실패해도 T1 이 만든 행(사용분 저장 포함)은 남고, 지갑은 차감되지 않는다")
  void T2_롤백_T1행유지() {
    // given
    String key = newKey();
    PaymentRequest request = preparedRequest();
    doThrow(new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS)).when(qrService).markUsed(any(), any()); // T2 실패처리
    // when
    PaymentResponse claimed = paymentIdempotencyService.claim(key, request);
    assertThatThrownBy(() -> paymentTxService.tx(claimed.paymentId())).isInstanceOf(BusinessException.class);
    Optional<Payment> payment = paymentRepository.findById(claimed.paymentId());
    // then : 행은 PROCESSING 으로 남고, 완료 시각은 없다
    assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    assertThat(payment.get().getCompletedAt()).isNull();
    // T1 이 접수 때 계산한 사용분은 그대로 저장되어 있다 (충전금 전액)
    assertThat(payment.get().getWalletUsedAmount()).isEqualTo(10_000L);
    // 지갑은 차감되지 않았다
    assertThat(walletRepository.findById(request.walletId()).orElseThrow().getBalance()).isEqualTo(10_000L);
  }

  @Test
  @DisplayName("T2 실패 후 같은 멱등키로 재요청하면 기존 FAILED 결과를 반환하고, 본처리는 다시 실행되지 않는다")
  void T2실패후_같은키_재요청() {
    // given : 첫 요청이 QR 처리 실패로 FAILED 가 된다
    String key = newKey();
    PaymentRequest request = preparedRequest();
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)).when(qrService).markUsed(any(), any());
    assertThatThrownBy(() -> paymentFacade.pay(key, request)).isInstanceOf(BusinessException.class);
    // when : 같은 멱등키, 같은 요청으로 다시 호출 (예외 없이 기존 결과를 돌려준다)
    PaymentResponse second = paymentFacade.pay(key, request);
    // then 1 : 응답은 기존 FAILED 결과
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(second.paymentId()).isEqualTo(payment.getId());
    assertThat(second.status()).isEqualTo(PaymentStatus.FAILED);
    // then 2 : 행은 그대로 FAILED (조회가 1건으로 나오는 것 자체가 행이 1개라는 뜻: 복합 유니크)
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    // then 3 : 두 번째 호출은 본처리(T2)에 가지 않았다 → QR 처리는 처음 1번만
    verify(qrService, times(1)).markUsed(any(), any());
  }

  @Test
  @DisplayName("T2 와 T3 가 모두 실패하면 행은 PROCESSING 으로 남는다")
  void T2_T3_모두실패_PROCESSING유지() {
    // given : T2 (QR 처리) 도 실패하고, T3 (실패 기록) 도 예외를 던진다
    String key = newKey();
    PaymentRequest request = preparedRequest();
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)).when(qrService).markUsed(any(), any());
    doThrow(new IllegalStateException("T3 실패")).when(paymentFailureService).failure(any(), any());
    // when : T3 의 예외가 그대로 올라온다
    assertThatThrownBy(() -> paymentFacade.pay(key, request)).isInstanceOf(IllegalStateException.class);
    // then : T1 이 만든 행은 PROCESSING 그대로, 실패 코드도 비어 있다
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    assertThat(payment.getFailureCode()).isNull();
  }

  @Test
  @DisplayName("T2 가 성공하면 T3 는 호출되지 않고 COMPLETED 로 남는다")
  void T2_성공시_T3_호출안됨() {
    // given
    String key = newKey();
    PaymentRequest request = preparedRequest();
    // when : QR 도 지갑도 정상
    PaymentResponse response = paymentFacade.pay(key, request);
    // then 1 : 응답과 DB 모두 COMPLETED
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.getFailureCode()).isNull();
    // then 2 : 실패 기록(T3)은 한 번도 호출되지 않았다
    verify(paymentFailureService, never()).failure(any(), any());
  }

  @Test
  @DisplayName("충전금이 부족하면 WAL-004 가 FAILED 의 failure_code 로 기록되고 지갑은 그대로다")
  void 충전금부족_WAL004_실패기록() {
    // given : 10,000원 결제인데 충전금은 5,000원
    String key = newKey();
    PaymentRequest request = preparedRequest(10_000L, 5_000L, 0L, 0L);
    // when
    assertThatThrownBy(() -> paymentFacade.pay(key, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WalletErrorCode.INSUFFICIENT_BALANCE);
    // then
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo("WAL-004");
    assertThat(walletRepository.findById(request.walletId()).orElseThrow().getBalance()).isEqualTo(5_000L);
  }

  @Test
  @DisplayName("캐시백이 부족하면 WAL-005 가 기록되고, 먼저 차감한 충전금도 함께 롤백된다")
  void 캐시백부족_WAL005_실패기록_충전금도롤백() {
    // given : 캐시백 3,000 을 쓰려는데 캐시백 잔액은 0원, 충전금은 7,000원
    String key = newKey();
    PaymentRequest request = preparedRequest(10_000L, 7_000L, 0L, 3_000L);
    // when
    assertThatThrownBy(() -> paymentFacade.pay(key, request))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WalletErrorCode.INSUFFICIENT_CASHBACK);
    // then : FAILED + WAL-005, 충전금 차감도 롤백되어 7,000 그대로
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo("WAL-005");
    assertThat(walletRepository.findById(request.walletId()).orElseThrow().getBalance()).isEqualTo(7_000L);
  }

  @Test
  @DisplayName("이미 COMPLETED 인 건에 T3 를 호출해도 상태가 바뀌지 않는다")
  void T3_완료건_상태유지() {
    // given : 정상 결제로 COMPLETED 가 된 건
    String key = newKey();
    PaymentRequest request = preparedRequest();
    PaymentResponse response = paymentFacade.pay(key, request);
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    // when : T3 를 직접 호출해도 예외 없이 끝난다
    paymentFailureService.failure(response.paymentId(), "QR-003");
    // then : 여전히 COMPLETED, 실패 코드는 비어 있다
    Payment payment = paymentRepository.findById(response.paymentId()).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.getFailureCode()).isNull();
  }

  @Test
  @DisplayName("이미 FAILED 인 건에 T3 를 다시 호출해도 처음 기록한 실패 코드가 유지된다")
  void T3_실패건_재호출시_기존코드유지() {
    // given : QR 처리 실패로 FAILED + RESOURCE_NOT_FOUND 코드가 기록된 건
    String key = newKey();
    PaymentRequest request = preparedRequest();
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)).when(qrService).markUsed(any(), any());
    assertThatThrownBy(() -> paymentFacade.pay(key, request)).isInstanceOf(BusinessException.class);
    Payment failed =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    // when : 다른 코드로 T3 를 다시 호출한다
    paymentFailureService.failure(failed.getId(), "QR-003");
    // then : 상태와 처음 기록한 코드가 그대로다
    Payment payment = paymentRepository.findById(failed.getId()).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.getCode());
  }
}
