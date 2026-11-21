package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode;
import com.back.nbe1214finalhot6.domain.payment.mock.QrMockService;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

/** 결제 결과 재조회(읽기 전용) 통합 테스트. 저장된 결제 행을 멱등키로 다시 읽어 오는지 검증한다. */
@IntegrationTest
class PaymentQueryServiceTest {

  @Autowired PaymentQueryService paymentQueryService;
  @Autowired PaymentFacade paymentFacade; // 결제 행을 만들 때 사용
  @Autowired PaymentIdempotencyService paymentIdempotencyService; // T1 만 실행해 PROCESSING 행 만들 때 사용
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantRepository merchantRepository;

  // 실패 건을 만들기 위해 QR 서비스를 Mock 으로 교체한다
  @MockitoBean QrMockService qrService;

  private Long saveMerchant() {
    return merchantRepository.save(Merchant.create(null, "테스트가맹점", "인천", true)).getId();
  }

  private PaymentRequest request(Long merchantId, long amount) {
    return new PaymentRequest(1L, merchantId, "qr-token-1", amount);
  }

  private String newKey() {
    return UUID.randomUUID().toString();
  }

  @Test
  @DisplayName("완료된 결제를 같은 멱등키로 조회하면 COMPLETED 결과(영수증 필드 포함)를 돌려준다")
  void 완료건_조회() {
    // given : 결제를 하나 완료시킨다
    Long merchantId = saveMerchant();
    String key = newKey();
    PaymentResponse paid = paymentFacade.pay(key, request(merchantId, 10_000L));
    assertThat(paid.status()).isEqualTo(PaymentStatus.COMPLETED);

    // when : 같은 지갑·같은 키로 재조회
    PaymentResponse found = paymentQueryService.findByKey(1L, key);

    // then : COMPLETED 이고, 결제 때 받은 응답과 같은 내용
    assertThat(found.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(found.paymentId()).isEqualTo(paid.paymentId());
    assertThat(found.amount()).isEqualTo(paid.amount());
    assertThat(found.walletUsedAmount()).isEqualTo(paid.walletUsedAmount());
    assertThat(found.cashbackUsedAmount()).isEqualTo(paid.cashbackUsedAmount());
    assertThat(found.cashbackEarnedAmount()).isEqualTo(paid.cashbackEarnedAmount());
    // 접수 시각과 완료 시각이 기록되어 있다
    assertThat(found.requestedAt()).isNotNull();
    assertThat(found.completedAt()).isNotNull();
  }

  @Test
  @DisplayName("실패한 결제를 조회하면 FAILED 와 실패 코드(failureCode)를 돌려준다")
  void 실패건_조회() {
    // given : QR 처리가 실패하도록 하고 결제를 요청한다 → FAILED 로 기록됨
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
        .when(qrService)
        .markUsed(any(), any(), anyLong(), any());
    Long merchantId = saveMerchant();
    String key = newKey();
    // 실패하면 pay 는 응답 대신 예외를 던진다 (실패 기록은 T3 가 남긴다)
    assertThatThrownBy(() -> paymentFacade.pay(key, request(merchantId, 10_000L)))
        .isInstanceOf(BusinessException.class);

    // when : 응답을 못 받은 클라이언트가 같은 키로 재조회
    PaymentResponse found = paymentQueryService.findByKey(1L, key);

    // then : T3 가 기록한 실패 결과를 그대로 돌려준다
    assertThat(found.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(found.failureCode()).isEqualTo("COM-009");
    assertThat(found.requestedAt()).isNotNull();
    assertThat(found.completedAt()).isNull(); // 실패 건은 완료 시각이 없다
  }

  @Test
  @DisplayName("처리 중(PROCESSING)인 결제는 에러 없이 상태 그대로 돌려준다 (실패로 단정하지 않음)")
  void 처리중건_조회() {
    // given : T1 만 실행해 PROCESSING 행을 만든다 (T2 는 아직)
    Long merchantId = saveMerchant();
    String key = newKey();
    paymentIdempotencyService.claim(key, request(merchantId, 10_000L));

    // when : 예외 없이 조회된다
    PaymentResponse found = paymentQueryService.findByKey(1L, key);

    // then : 실패로 단정하지 않고 PROCESSING 그대로, 실패 코드 없음
    assertThat(found.status()).isEqualTo(PaymentStatus.PROCESSING);
    assertThat(found.failureCode()).isNull();
    assertThat(found.requestedAt()).isNotNull(); // 접수 시각은 있다
    assertThat(found.completedAt()).isNull(); // 아직 끝나지 않았다
  }

  @Test
  @DisplayName("없는 멱등키로 조회하면 PAYMENT_NOT_FOUND(PAY-003) 예외가 난다")
  void 없는키_조회() {
    // when & then
    assertThatThrownBy(() -> paymentQueryService.findByKey(1L, newKey()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
  }

  @Test
  @DisplayName("다른 지갑의 멱등키로 조회하면 없는 것으로 취급되어 PAYMENT_NOT_FOUND 예외가 난다")
  void 다른지갑의키_조회불가() {
    // given : 1번 지갑으로 결제를 하나 만든다
    Long merchantId = saveMerchant();
    String key = newKey();
    paymentFacade.pay(key, request(merchantId, 10_000L));

    // when & then : 같은 키라도 2번 지갑으로는 조회되지 않는다
    assertThatThrownBy(() -> paymentQueryService.findByKey(2L, key))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
  }

  @Test
  @DisplayName("조회는 읽기만 하고 결제를 새로 처리하지 않는다 (행 개수와 상태가 그대로)")
  void 조회는_결제를_처리하지_않는다() {
    // given : PROCESSING 행 하나 (조회가 결제를 처리한다면 COMPLETED 로 바뀔 것이다)
    Long merchantId = saveMerchant();
    String key = newKey();
    Long paymentId = paymentIdempotencyService.claim(key, request(merchantId, 10_000L)).paymentId();
    long countBefore = paymentRepository.count();

    // when : 여러 번 조회
    paymentQueryService.findByKey(1L, key);
    paymentQueryService.findByKey(1L, key);

    // then : 행 개수 그대로, 상태도 PROCESSING 그대로
    assertThat(paymentRepository.count()).isEqualTo(countBefore);
    assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.PROCESSING);
  }
}
