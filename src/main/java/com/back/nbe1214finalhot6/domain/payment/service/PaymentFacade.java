package com.back.nbe1214finalhot6.domain.payment.service;

import static com.back.nbe1214finalhot6.common.response.CommonErrorCode.IDEMPOTENCY_IN_PROGRESS;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
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
  private final PaymentTxService paymentTxService;
  private final PaymentFailureService paymentFailureService;

  public PaymentResponse pay(String key, PaymentRequest request) {
    // T2 로 넘길 대상(T1 이 선점한 행). 신규 요청일 때만 채워진다 (기존 행이 있으면 아래 catch 에서 끝난다)
    PaymentResponse claimed;
    try {
          // Transaction 1. 선점처리 claim
          claimed = paymentIdempotencyService.claim(key, request);

    } catch (DataIntegrityViolationException e) { // 기존행이 있는 경우
      PaymentResponse existing = paymentIdempotencyService.findExistingResult(key, request);

      // 완료 / 취소 / 실패: 저장된 결과를 그대로 반환 (T2 로 가지 않음)
      // FAILED 도 같은 멱등키로는 재시도하지 않고 저장된 결과를 돌려준다 (다시 결제하려면 새 멱등키로 요청)
      if (existing.status() == PaymentStatus.COMPLETED
          || existing.status() == PaymentStatus.CANCELED
          || existing.status() == PaymentStatus.FAILED) {
        return existing;
      }

      // 처리 중(PROCESSING): 다른 요청이 처리하고 있다
      throw new BusinessException(IDEMPOTENCY_IN_PROGRESS);
    }

    // Transaction 2. 본처리
    // T2 는 위 try 밖에서 호출: 결제 중 DataIntegrityViolationException 이 위 catch 로 빠지지 않게
    try {
      return paymentTxService.tx(claimed.paymentId());
    } catch (BusinessException e) { // 예상한 실패 (에러 코드가 있음)
      // IDEMPOTENCY_IN_PROGRESS 는 "다른 요청이 처리 중/완료"라는 뜻이라 기록하면 안 된다
      if (e.getErrorCode() != CommonErrorCode.IDEMPOTENCY_IN_PROGRESS) {
        paymentFailureService.failure(claimed.paymentId(), e.getErrorCode().getCode());
      }
      throw e;
    } catch (RuntimeException e) { // 예상 못 한 실패 (코드 없음 → COM-010)
      paymentFailureService.failure(
          claimed.paymentId(), CommonErrorCode.INTERNAL_SERVER_ERROR.getCode());
      throw e;
    }
  }
}
