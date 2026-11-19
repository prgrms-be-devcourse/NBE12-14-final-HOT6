package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;

import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class PaymentFacadeTest {

  @Autowired PaymentFacade paymentFacade;
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantRepository merchantRepository;
  @Autowired MutableClock clock;

  //수정 : 본처리가 가맹점(cashbackEligible)을 조회하므로 실제로 저장된 가맹점 id 를 쓴다
  private Long saveMerchant() {
    return merchantRepository
            .save(Merchant.create(null, "테스트가맹점", "인천", true)).getId();
  }

  /*
    1. 새로운 멱등키 요청 => '진행중' 처리 => '완료' 처리
    2. 같은 멱등키, 다른(금액,가맹점,큐알)요청 => 에러
    3. 같은 멱등키, 같은 요청, '진행중'상태 => 에러
    4. 같은 멱등키, 같은 요청, '완료'상태 => 기존결과 반환,행 1개, 상태유지('완료')
    5. 같은 멱등키, 같은 요청, '실패'상태 => 재시도가능이므로 '진행중'처리
   */
  @Test
  @DisplayName("새로운 멱등키 요청 => '완료' 처리")
  void 새로운멱등키요청(){
    //given
    Long merchantId = saveMerchant();
    String key = UUID.randomUUID().toString();
    PaymentRequest request = new PaymentRequest(
            1L, merchantId, "qr-token-1", 10_000L
    );
    //when
    PaymentResponse response = paymentFacade.pay(key,request);
    //then 1 : 응답확인
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    //then 2 : DB확인
    Payment saved = paymentRepository
                                    .findByWalletIdAndIdempotencyKey(request.walletId(),key)
                                    .orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    // assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }
  @Test
  @DisplayName("결제 완료 시 접수 시각·완료 시각이 Clock(KST) 기준으로 저장된다")
  void 완료시각검증() {
    //given : 시계를 2026-11-18 10:30 KST (= 01:30 UTC) 로 고정
    clock.set(Instant.parse("2026-11-18T01:30:00Z"));
    Long merchantId = saveMerchant();
    String key = UUID.randomUUID().toString();
    PaymentRequest request = new PaymentRequest(1L, merchantId, "qr-token-1", 10_000L);
    //when
    PaymentResponse response = paymentFacade.pay(key, request);
    //then
    Payment saved = paymentRepository.findById(response.paymentId()).orElseThrow();
    LocalDateTime expected = LocalDateTime.of(2026, 11, 18, 10, 30);
    assertThat(saved.getRequestedAt()).isEqualTo(expected);
    assertThat(saved.getCompletedAt()).isEqualTo(expected);
  }

  @Test
  @DisplayName("같은 멱등키, 다른(금액,가맹점,큐알)요청 => 에러")
  void 같은멱등키다른요청(){
    //given
    Long merchantId = saveMerchant();
    String key = UUID.randomUUID().toString();
    PaymentRequest pastrequest = new PaymentRequest(
            1L, merchantId, "qr-token-1", 10_000L
    );
    PaymentRequest currentrequest = new PaymentRequest(
            1L, merchantId + 1, "qr-token-2", 20_000L //walletId 는 같아야 함 ((walletId, 멱등키)가 유니크라서 지갑이 다르면 새 요청이 됨)
    );
    paymentFacade.pay(key, pastrequest); //먼저 최초 요청으로 기존 행을 만들어 둠

    //when & then
    assertThatThrownBy(() -> paymentFacade.pay(key, currentrequest))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_MISMATCH);
  }

  @Test
  @DisplayName("같은 멱등키, 같은 요청, '진행중'상태 => 에러")
  void 같은멱등키같은요청인데진행중상태(){
    //given
    Long merchantId = saveMerchant();
    String key = UUID.randomUUID().toString();
    PaymentRequest pastrequest = new PaymentRequest(
            1L, merchantId, "qr-token-1", 10_000L
    );
    PaymentRequest currentrequest = new PaymentRequest(
            1L, merchantId, "qr-token-1", 10_000L
    );
    PaymentResponse response = paymentFacade.pay(key, pastrequest); //먼저 최초 요청으로 기존 행을 만들어 둠
    assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
    paymentRepository.updateStatusIfCurrent(response.paymentId(),PaymentStatus.COMPLETED,PaymentStatus.PROCESSING);

    //when & then
    assertThatThrownBy(() -> paymentFacade.pay(key, currentrequest))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS); //수정 : COM-004 (진행 중). 상태 비교(PaymentStatus) 줄과 MISMATCH 줄 제거
  }
  @Test
  @DisplayName("같은 멱등키, 같은 요청, '완료'상태 => 기존결과 반환,행 1개, 상태유지('완료')")
  void 같은멱등키같은요청인데완료상태(){
    //given
    Long merchantId = saveMerchant();
    String key = UUID.randomUUID().toString();
    PaymentRequest pastrequest = new PaymentRequest(
            1L, merchantId, "qr-token-1", 10_000L
    );
    PaymentRequest currentrequest = new PaymentRequest(
            1L, merchantId, "qr-token-1", 10_000L
    );
    //when & then
    PaymentResponse pastresponse = paymentFacade.pay(key, pastrequest); //기존요청
    PaymentResponse currentresponse = paymentFacade.pay(key, currentrequest);
    assertThat(currentresponse.paymentId()).isEqualTo(pastresponse.paymentId());
    assertThat(currentresponse.status()).isEqualTo(PaymentStatus.COMPLETED);

  }

  @Disabled("FAILED 재시도 정책 확정 및 T2 연결 후 활성화")
  @Test
  @DisplayName("같은 멱등키, 같은 요청, '실패'상태 => 재시도가능이므로 '진행중'처리")
  void 같은멱등키같은요청인데실패상태(){
    //given
    Long merchantId = saveMerchant();
    String key = UUID.randomUUID().toString();
    PaymentRequest pastrequest = new PaymentRequest(
            1L, merchantId, "qr-token-1", 10_000L
    );
    PaymentRequest currentrequest = new PaymentRequest(
            1L, merchantId, "qr-token-1", 10_000L
    );
    PaymentResponse pastresponse = paymentFacade.pay(key, pastrequest); //기존요청
    //기존요청 완료처리
    paymentRepository.updateStatusIfCurrent(pastresponse.paymentId(),PaymentStatus.PROCESSING,PaymentStatus.FAILED);
    PaymentResponse currentresponse = paymentFacade.pay(key, currentrequest);
    assertThat(currentresponse.status()).isEqualTo(PaymentStatus.PROCESSING);
    //when & then

  }
}
