package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
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
  private final CashbackEarningRepository earningRepository;
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

    // 임시: #116이 지급 후 취소에 CSH-010을 던진다. 예외는 이 트랜잭션을 rollback-only로 만들어
    // 잡아도 살릴 수 없어서, 지급된 적립은 호출 전에 건너뛴다. CancelOutcome 도입 후 제거(TODO #74)
    boolean alreadyPaid = earningRepository.findByPaymentId(paymentId)
        .map(e -> e.getStatus() == EarningStatus.PAID)
        .orElse(false);
    if (alreadyPaid) {
      log.warn("지급된 캐시백이 있는 결제를 취소함. 회수 필요 paymentId={}", paymentId);
    } else {
      cashbackService.cancel(paymentId);
    }

    payment.cancel(LocalDateTime.now(clock));

    return PaymentResponse.of(payment);
  }
}
