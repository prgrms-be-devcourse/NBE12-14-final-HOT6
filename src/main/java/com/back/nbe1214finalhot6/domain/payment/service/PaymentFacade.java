package com.back.nbe1214finalhot6.domain.payment.service;

import static com.back.nbe1214finalhot6.common.response.CommonErrorCode.IDEMPOTENCY_IN_PROGRESS;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PaymentFacade {

  private final PaymentIdempotencyService paymentIdempotencyService;

  public PaymentResponse pay(String key, PaymentRequest request) {
    try {
      // Transaction 1. 선점처리 claim
      return paymentIdempotencyService.claim(key, request);
    } catch (DataIntegrityViolationException e) { // 기존행이 있는 경우
      PaymentResponse existing = paymentIdempotencyService.findExistingResult(key, request);
      // 완료 / 취소: 저장된 결과를 그대로 반환 (T2 로 가지 않음)
      if (existing.status() == PaymentStatus.COMPLETED || existing.status() == PaymentStatus.CANCELED) {
        return existing;
      }
      // 실패: 조건부 UPDATE 로 재시도 권한을 얻은 한 요청만 T2 진행
      if (existing.status() == PaymentStatus.FAILED) {
        if (paymentIdempotencyService.reclaimFailed(existing.paymentId())) {
          // TODO: Transaction 2-1. existing.paymentId() 행으로 실제 결제 처리 (재시도)
          // TODO: 재시도 시 failureCode 초기화 여부, FAILED 재시도 허용 정책 확정 후 반영
          return existing; // TODO: T2 연결 후 처리 결과로 교체
        }
        throw new BusinessException(IDEMPOTENCY_IN_PROGRESS);
      }
      throw new BusinessException(IDEMPOTENCY_IN_PROGRESS);
    }
    // TODO: Transaction 2-2. 선점 이후 실제 결제 처리 연결
    //  (T2/T3 는 try 밖으로 분리: 결제 중 DataIntegrityViolationException 이 아래 catch 로 빠지지 않게)
    // TODO: Transaction 3. 결제 이후 실제 실패 처리
  }
}
