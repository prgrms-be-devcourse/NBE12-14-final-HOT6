package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.ReserveCommand;
import com.back.nbe1214finalhot6.domain.cashback.dto.ReserveResult;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.mock.QrMockService;
import com.back.nbe1214finalhot6.domain.payment.mock.WalletMockService;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
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
  // TODO: 실제 WalletService / QrService  가 머지되면 타입 교체 후 mock 삭제
  private final WalletMockService walletService;
  private final QrMockService qrService;
  private final CashbackService cashbackService;
  private final MerchantService merchantService;
  private final Clock clock;

  // Transaction 2. 본처리 (T1 이 선점한 paymentId 행을 이어받아 처리)
  @Transactional
  public PaymentResponse tx(Long paymentId) {
    // 1. 결제 행 락 조회 (paymentRepository.findByIdWithLock) 후 PROCESSING 인지 확인
    Payment payment = paymentRepository
                      .findByIdWithLock(paymentId)
                      .orElseThrow(() -> new BusinessException(RESOURCE_NOT_FOUND));
    if (payment.getStatus() != PaymentStatus.PROCESSING) {
      throw new BusinessException(IDEMPOTENCY_IN_PROGRESS);   // 이미 다른 요청이 처리함 → 진행하지 않음
    }
    // TODO 2. 지갑 차감: walletService.debit(walletId, amount) => 데브머니/캐시백 사용분
    WalletMockService.DebitResult debit = walletService.debit(payment.getWalletId(),payment.getAmount());
    // TODO 3. QR 사용 처리: qrService.markUsed(qrId) => 이미 사용됐으면 예외 (전체 롤백)
    qrService.markUsed(payment.getQrId());
    // 4. 적립 예정 확보: 가맹점의 cashbackEligible 을 읽어 cashbackService.reserve(command)
    boolean cashbackEligible = merchantService.isCashbackEligible(payment.getMerchantId());

    ReserveResult reserve = cashbackService.reserve(
      new ReserveCommand( //결제id,지갑id,캐시백적립여부,지갑사용액,결제요청시간
              paymentId,
              payment.getWalletId(),
              cashbackEligible,
              debit.walletUsed(),
              payment.getRequestedAt()
      )
    );
    // TODO 5. 결제 완료 처리
    payment.complete(
        debit.walletUsed(),
        debit.cashbackUsed(),
        reserve.appliedPolicyId(),
        reserve.appliedRate(),
        reserve.earnedAmount(),
        reserve.payableAt(),
        reserve.exclusionReason().name(),
        LocalDateTime.now(clock));
    return PaymentResponse.of(payment);
  }
}
