package com.back.nbe1214finalhot6.domain.payment.service;


import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.back.nbe1214finalhot6.common.response.CommonErrorCode.RESOURCE_NOT_FOUND;


@RequiredArgsConstructor
@Service
public class PaymentFailureService {
    private final PaymentRepository paymentRepository;

    //T2가 롤백된 뒤, 트랜잭션 밖에서 PROCESSING → FAILED와 failure_code를 기록합니다.
    @Transactional
    public void failure(Long paymentId, String failureCode) {
        Payment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new BusinessException(RESOURCE_NOT_FOUND));
        if (payment.getStatus() != PaymentStatus.PROCESSING) return; // 이미 다른 요청이 마무리함 → 건드리지 않음
        payment.failure(failureCode); // 변경 감지로 UPDATE
    }
}
