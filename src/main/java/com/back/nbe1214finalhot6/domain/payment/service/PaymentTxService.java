package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveResponse;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.mock.QrMockService;
import com.back.nbe1214finalhot6.domain.payment.mock.WalletMockService;
import com.back.nbe1214finalhot6.domain.payment.mock.WalletTxType;
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
    // TODO 2. 지갑 차감: debit(walletId, amount, type, refKey, allowNegative) => 데브머니/캐시백 사용분
    //  refKey 는 paymentId: 같은 결제의 차감이 두 번 반영되지 않게 원장 (type, refKey) 유니크로 막는다
    WalletMockService.DebitResult debit =
        walletService.debit(
            payment.getWalletId(), payment.getAmount(), WalletTxType.PAYMENT, paymentId, false);
    // TODO 3. QR 사용 처리: markUsed(qrToken, merchantId, amount, requestedAt)
    //  => 매장·금액·만료·사용 여부를 D 가 조건부 UPDATE 로 확인, 실패하면 예외 (전체 롤백)
    //  만료는 최초 접수 시각(requestedAt) 기준
    qrService.markUsed(
        payment.getQrToken(), payment.getMerchantId(), payment.getAmount(), payment.getRequestedAt());
    // 4. 적립 예정 확보: 가맹점의 cashbackEligible 을 읽어 cashbackService.reserve(command)
    boolean cashbackEligible = merchantService.isCashbackEligible(payment.getMerchantId());

    CashbackReserveResponse reserve = cashbackService.reserve(
      new CashbackReserveRequest( //결제id,지갑id,캐시백적립여부,지갑사용액,결제요청시간
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
    // TODO 6. 가맹점 매출 기록 (D): 결제 완료 처리 직후, 같은 트랜잭션 안에서 호출한다 (T2 롤백 시 함께 롤백)
    //  recordPayment(paymentId, merchantId, amount, walletUsed, cashbackUsed, completedAt)
    //  completedAt 은 위 complete(...) 에 넘긴 시각과 같은 값을 쓴다
    //  (지금은 LocalDateTime.now(clock) 을 인라인으로 넘기고 있어서, 호출 시 변수로 빼야 한다)
    return PaymentResponse.of(payment);
  }
}
