package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode.PAYMENT_MERCHANT_MISMATCH;
import static com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode.PAYMENT_NOT_CANCELABLE;
import static com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode.PAYMENT_NOT_FOUND;

@RequiredArgsConstructor
@Service
@Slf4j
public class PaymentCancelService {

  private final PaymentRepository paymentRepository;
  private final WalletService walletService;
  private final CashbackService cashbackService;
  private final Clock clock;

  // 결제 취소 (단일 트랜잭션). 락 순서: 결제 → 지갑 → 예산
  @Transactional
  public PaymentResponse cancel(Long merchantId, Long paymentId) {

    Payment payment = paymentRepository
        .findByIdWithLock(paymentId)
        .orElseThrow(() -> new BusinessException(PAYMENT_NOT_FOUND));

    if (!payment.getMerchantId().equals(merchantId)) {
      throw new BusinessException(PAYMENT_MERCHANT_MISMATCH);
    }

    // 중복 취소: 에러 없이 기존 결과 반환
    if (payment.getStatus() == PaymentStatus.CANCELED) {
      return PaymentResponse.of(payment);
    }
    if (payment.getStatus() != PaymentStatus.COMPLETED) {
      throw new BusinessException(PAYMENT_NOT_CANCELABLE);
    }

    walletService.creditPaymentCancel(
        payment.getWalletId(),
        payment.getWalletUsedAmount(),
        payment.getCashbackUsedAmount(),
        paymentId);


    cashbackService.cancel(paymentId);

    payment.cancel(LocalDateTime.now(clock));

    return PaymentResponse.of(payment);
  }
}
