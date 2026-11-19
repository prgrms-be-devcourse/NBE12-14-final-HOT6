package com.back.nbe1214finalhot6.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.mock.QrMockService;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** T3(실패 처리) 통합 테스트. T2 에서 예외가 나면 T1 이 만든 행이 FAILED + failure_code 로 남는지 검증한다. */
@IntegrationTest
class PaymentFacadeFailureTest {

  @Autowired PaymentFacade paymentFacade;
  @Autowired PaymentIdempotencyService paymentIdempotencyService; // T1: 행 만들기용
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantRepository merchantRepository;

  // 실패 상황을 만들기 위해 QR 서비스를 Mock 으로 교체한다 (지갑은 기본 Mock 구현 그대로 사용)
  @MockitoBean QrMockService qrService;
  @Autowired
  private PaymentTxService paymentTxService;
  // 실제 동작은 그대로 두고, 필요한 테스트에서만 예외를 던지게 하거나 호출 여부를 확인한다 (Spy)
  @MockitoSpyBean
  private PaymentFailureService paymentFailureService;

  private Long saveMerchant() {
    return merchantRepository.save(Merchant.create(null, "테스트가맹점", "인천", true)).getId();
  }

  // 결제 요청 (PaymentTxServiceTest 와 동일한 헬퍼)
  private PaymentRequest request(Long merchantId, long amount) {
    return new PaymentRequest(1L, merchantId, "qr-token-1", amount);
  }

  // 새 멱등키 (PaymentTxServiceTest 와 동일)
  private String newKey() {
    return UUID.randomUUID().toString();
  }

  // PROCESSING 상태의 결제 행을 만들고 그 id 를 돌려준다 (T1, PaymentTxServiceTest 와 동일)
  private Long claimPayment(Long merchantId, long amount) {
    return paymentIdempotencyService.claim(newKey(), request(merchantId, amount)).paymentId();
  }

  @Test
  @DisplayName("T2 가 BusinessException 으로 실패하면 FAILED + 해당 에러 코드가 기록되고 예외가 전파된다")
  void T2_비즈니스예외_실패기록() {
    // given
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)).when(qrService).markUsed(any(), any(), anyLong(), any());
    // when
    assertThatThrownBy(()-> paymentFacade.pay(key,request)).isInstanceOf(BusinessException.class);
    // then
    Payment payment = paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(),key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.getCode());
  }

  @Test
  @DisplayName("T2 가 일반 RuntimeException 으로 실패하면 FAILED + COM-010 이 기록된다")
  void T2_일반예외_실패기록() {
    // given
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    doThrow(new RuntimeException()).when(qrService).markUsed(any(), any(), anyLong(), any());
    // when
    // assertThatThrownBy : 예외를 받아내서 실제예외가 났다는 결과값만 세팅하는 역할
    // isInstanceOf : 예외종류확인용
    assertThatThrownBy(()-> paymentFacade.pay(key,request)).isInstanceOf(RuntimeException.class);
    // then
    Payment payment = paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(),key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.getCode());
  }

  @Test
  @DisplayName("T2 가 IDEMPOTENCY_IN_PROGRESS 를 던지면 FAILED 로 바꾸지 않고 PROCESSING 을 유지한다")
  void T2_멱등키처리중예외_실패기록안함() {
    //given
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    doThrow(new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS)).when(qrService).markUsed(any(), any(), anyLong(), any());
    //when
    assertThatThrownBy(()-> paymentFacade.pay(key,request)).isInstanceOf(BusinessException.class);
    //then
    Payment payment = paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(),key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    assertThat(payment.getFailureCode()).isEqualTo(null);
  }

  @Test
  @DisplayName("T2 가 실패해도 T1 이 만든 행은 남고, T2 의 변경(지갑·QR·캐시백)은 롤백된다")
  void T2_롤백_T1행유지() {
    //given
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    doThrow(new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS)).when(qrService).markUsed(any(), any(), anyLong(), any()); //T2실패처리
    //when
    PaymentResponse claimed = paymentIdempotencyService.claim(key, request);
    assertThatThrownBy(() -> paymentTxService.tx(claimed.paymentId())).isInstanceOf(BusinessException.class);
    Optional<Payment> payment = paymentRepository.findById(claimed.paymentId());
    assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    //then
    //completedAt이 null이고 walletUsedAmount가 0인지도 같이
    assertThat(payment.get().getCompletedAt()).isNull();
    assertThat(payment.get().getWalletUsedAmount()).isEqualTo(0);
  }

  @Test
  @DisplayName("T2 실패 후 같은 멱등키로 재요청하면 기존 FAILED 결과를 반환하고, 본처리는 다시 실행되지 않는다")
  void T2실패후_같은키_재요청() {
    //given : 첫 요청이 QR 처리 실패로 FAILED 가 된다
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
        .when(qrService).markUsed(any(), any(), anyLong(), any());
    assertThatThrownBy(() -> paymentFacade.pay(key, request)).isInstanceOf(BusinessException.class);
    //when : 같은 멱등키, 같은 요청으로 다시 호출 (예외 없이 기존 결과를 돌려준다)
    PaymentResponse second = paymentFacade.pay(key, request);
    //then 1 : 응답은 기존 FAILED 결과
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(second.paymentId()).isEqualTo(payment.getId());
    assertThat(second.status()).isEqualTo(PaymentStatus.FAILED);
    //then 2 : 행은 그대로 FAILED (조회가 1건으로 나오는 것 자체가 행이 1개라는 뜻: 복합 유니크)
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    //then 3 : 두 번째 호출은 본처리(T2)에 가지 않았다 → QR 처리는 처음 1번만
    verify(qrService, times(1)).markUsed(any(), any(), anyLong(), any());
  }

  @Test
  @DisplayName("T2 와 T3 가 모두 실패하면 행은 PROCESSING 으로 남는다")
  void T2_T3_모두실패_PROCESSING유지() {
    //given : T2 (QR 처리) 도 실패하고, T3 (실패 기록) 도 예외를 던진다
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
        .when(qrService).markUsed(any(), any(), anyLong(), any());
    doThrow(new IllegalStateException("T3 실패"))
        .when(paymentFailureService).failure(any(), any());
    //when : T3 의 예외가 그대로 올라온다
    assertThatThrownBy(() -> paymentFacade.pay(key, request))
        .isInstanceOf(IllegalStateException.class);
    //then : T1 이 만든 행은 PROCESSING 그대로, 실패 코드도 비어 있다
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    assertThat(payment.getFailureCode()).isNull();
  }

  @Test
  @DisplayName("T2 가 성공하면 T3 는 호출되지 않고 COMPLETED 로 남는다")
  void T2_성공시_T3_호출안됨() {
    //given
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    //when : QR 도 지갑도 정상 (Mock 은 아무것도 던지지 않는다)
    PaymentResponse response = paymentFacade.pay(key, request);
    //then 1 : 응답과 DB 모두 COMPLETED
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    Payment payment =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.getFailureCode()).isNull();
    //then 2 : 실패 기록(T3)은 한 번도 호출되지 않았다
    verify(paymentFailureService, never()).failure(any(), any());
  }

  @Test
  @DisplayName("이미 COMPLETED 인 건에 T3 를 호출해도 상태가 바뀌지 않는다")
  void T3_완료건_상태유지() {
    //given : 정상 결제로 COMPLETED 가 된 건
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    PaymentResponse response = paymentFacade.pay(key, request);
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    //when : T3 를 직접 호출해도 예외 없이 끝난다
    paymentFailureService.failure(response.paymentId(), "QR-003");
    //then : 여전히 COMPLETED, 실패 코드는 비어 있다
    Payment payment = paymentRepository.findById(response.paymentId()).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.getFailureCode()).isNull();
  }

  @Test
  @DisplayName("이미 FAILED 인 건에 T3 를 다시 호출해도 처음 기록한 실패 코드가 유지된다")
  void T3_실패건_재호출시_기존코드유지() {
    //given : QR 처리 실패로 FAILED + RESOURCE_NOT_FOUND 코드가 기록된 건
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentRequest request = request(merchantId, 10_000L);
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
        .when(qrService).markUsed(any(), any(), anyLong(), any());
    assertThatThrownBy(() -> paymentFacade.pay(key, request)).isInstanceOf(BusinessException.class);
    Payment failed =
        paymentRepository.findByWalletIdAndIdempotencyKey(request.walletId(), key).orElseThrow();
    //when : 다른 코드로 T3 를 다시 호출한다
    paymentFailureService.failure(failed.getId(), "QR-003");
    //then : 상태와 처음 기록한 코드가 그대로다
    Payment payment = paymentRepository.findById(failed.getId()).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND.getCode());
  }
}
