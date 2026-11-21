package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.back.nbe1214finalhot6.common.response.CommonErrorCode.IDEMPOTENCY_IN_PROGRESS;
import static com.back.nbe1214finalhot6.common.response.CommonErrorCode.RESOURCE_NOT_FOUND;

@RequiredArgsConstructor
@Service
public class PaymentTxService {

  private final PaymentRepository paymentRepository;
  private final WalletService walletService;
  private final QrService qrService;
  private final CashbackService cashbackService;
  private final MerchantService merchantService;
  private final Clock clock;

  // Transaction 2. 본처리 (T1 이 선점한 paymentId 행을 이어받아 처리)
  @Transactional
  public PaymentResponse tx(Long paymentId) {

    // 결제 행 락 조회 (paymentRepository.findByIdWithLock) 후 PROCESSING 인지 확인
    Payment payment = paymentRepository
                      .findByIdWithLock(paymentId)
                      .orElseThrow(() -> new BusinessException(RESOURCE_NOT_FOUND));
    if (payment.getStatus() != PaymentStatus.PROCESSING) {
      throw new BusinessException(IDEMPOTENCY_IN_PROGRESS);   // 이미 다른 요청이 처리함 → 진행하지 않음
    }
    // 큐알 > 지갑 > 매출기록 > 예산
    qrService.markUsed(payment.getQrToken(), payment.getRequestedAt());

    walletService.debitPayment(payment.getWalletId(), payment.getWalletUsedAmount(),
                               payment.getCashbackUsedAmount(), paymentId);

    boolean cashbackEligible = merchantService.isCashbackEligible(payment.getMerchantId());
    // TODO 6. 가맹점 정산을 위한 매출 기록
    CashbackReserveResponse reserve = cashbackService.reserve(
      new CashbackReserveRequest(
              paymentId,
              payment.getWalletId(),
              cashbackEligible,
              payment.getWalletUsedAmount(),
              payment.getRequestedAt()
      )
    );
    payment.complete(
        payment.getWalletUsedAmount(),
        payment.getCashbackUsedAmount(),
        reserve.appliedPolicyId(),
        reserve.appliedRate(),
        reserve.earnedAmount(),
        reserve.payableAt(),
        reserve.exclusionReason().name(),
        LocalDateTime.now(clock));

    return PaymentResponse.of(payment);
  }
}
