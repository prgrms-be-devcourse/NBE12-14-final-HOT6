package com.back.nbe1214finalhot6.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

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

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * T2(본처리) 단위 검증. Facade 를 거치지 않고 T1 로 PROCESSING 행을 직접 만든 뒤 tx() 를 호출한다.
 *
 * <p>주의: 클래스에 @Transactional 을 붙이지 않는다 (T2 의 커밋/롤백을 실제로 확인해야 하므로).
 */
@IntegrationTest
class PaymentTxServiceTest {

  @Autowired PaymentTxService paymentTxService;
  @Autowired PaymentIdempotencyService paymentIdempotencyService; // T1: 행 만들기용
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantRepository merchantRepository;

  // 롤백 테스트용: QR 처리를 가짜로 바꿔서 예외를 던지게 한다
  @MockitoBean QrMockService qrService;

  private Long saveMerchant() {
    return merchantRepository
            .save(Merchant.create(null, "테스트가맹점", "인천", true)).getId();
  }

  // 결제 요청 (다른 결제 테스트와 동일한 헬퍼)
  private PaymentRequest request(Long merchantId, long amount) {
    return new PaymentRequest(1L, merchantId, "qr-token-1", amount);
  }

  // 새 멱등키 (PaymentFacadeFailureTest 와 동일)
  private String newKey() {
    return UUID.randomUUID().toString();
  }

  // PROCESSING 상태의 결제 행을 만들고 그 id 를 돌려준다 (T1)
  private Long claimPayment(Long merchantId, long amount) {
    return paymentIdempotencyService.claim(newKey(), request(merchantId, amount)).paymentId();
  }

  @Test
  @DisplayName("정상 처리 => COMPLETED, 사용분 합계 == 결제금액, 완료 시각 기록")
  void 정상처리() {
    // given
    Long merchantId = saveMerchant();
    Long paymentId = claimPayment(merchantId,10_000L);
    // when
    PaymentResponse response = paymentTxService.tx(paymentId);
    // then 1 : 응답 확인
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(response.walletUsedAmount() + response.cashbackUsedAmount()).isEqualTo(response.amount());
    // then 2 : DB 확인
    Payment saved = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(saved.getCompletedAt()).isNotNull();
  }

  @Test
  @DisplayName("QR 처리 실패 => 예외 + 전체 롤백 (행은 PROCESSING 그대로)")
  void QR실패시롤백() {
    // given
    Long merchantId = saveMerchant();
    Long paymentId = claimPayment(merchantId,10_000L);
    doThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND)).when(qrService).markUsed(any(), any(), anyLong(), any());
    // when & then 1 : 예외 확인
    assertThatThrownBy(()-> paymentTxService.tx(paymentId)).isInstanceOf(BusinessException.class);
    // then 2 : 롤백 확인
    Payment saved = paymentRepository.findById(paymentId).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
  }
}
