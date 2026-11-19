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
  private final PaymentTxService paymentTxService;

  public PaymentResponse pay(String key, PaymentRequest request) {
    // T2 로 넘길 대상(선점한 행). 신규 요청이거나 FAILED 재시도 권한을 얻은 경우에만 채워진다
    PaymentResponse claimed;

    try {
      // Transaction 1. 선점처리 claim
      claimed = paymentIdempotencyService.claim(key, request);

    } catch (DataIntegrityViolationException e) { // 기존행이 있는 경우
      PaymentResponse existing = paymentIdempotencyService.findExistingResult(key, request);

      // 완료 / 취소: 저장된 결과를 그대로 반환 (T2 로 가지 않음)
      if (existing.status() == PaymentStatus.COMPLETED
          || existing.status() == PaymentStatus.CANCELED) {
        return existing;
      }

      // 실패: 조건부 UPDATE 로 재시도 권한을 얻은 한 요청만 T2 진행
      // TODO: 재시도 시 failureCode 초기화 여부, FAILED 재시도 허용 정책 확정 후 반영
      if (existing.status() == PaymentStatus.FAILED
          && paymentIdempotencyService.reclaimFailed(existing.paymentId())) {
        claimed = existing;
      } else {
        // 재시도 권한을 못 얻었거나(다른 요청이 먼저 선점) 그 외 상태
        throw new BusinessException(IDEMPOTENCY_IN_PROGRESS);
      }
    }

    // Transaction 2. 본처리 (신규 요청과 FAILED 재시도가 같은 경로)
    // T2 는 위 try 밖에서 호출: 결제 중 DataIntegrityViolationException 이 위 catch 로 빠지지 않게
    // TODO: Transaction 3. 결제 이후 실제 실패 처리 (이번 범위 아님)
    return paymentTxService.tx(claimed.paymentId());
  }
}
