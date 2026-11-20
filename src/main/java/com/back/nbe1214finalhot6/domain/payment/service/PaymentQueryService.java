package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제 결과 재조회 (읽기 전용). 결제를 새로 처리하지 않고 저장된 결과만 돌려준다. */
@RequiredArgsConstructor
@Service
public class PaymentQueryService {

  private final PaymentRepository paymentRepository;

  // (walletId, 멱등키)로 찾으므로 다른 지갑의 키는 없는 것으로 취급된다 (404)
  @Transactional(readOnly = true)
  public PaymentResponse findByKey(Long walletId, String key) {
    return paymentRepository
        .findByWalletIdAndIdempotencyKey(walletId, key)
        .map(PaymentResponse::of)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
  }
}
