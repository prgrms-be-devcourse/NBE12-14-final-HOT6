package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import com.back.nbe1214finalhot6.domain.qr.dto.QrPaymentTarget;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.back.nbe1214finalhot6.common.response.CommonErrorCode.*;

@RequiredArgsConstructor
@Service
public class PaymentIdempotencyService {

  private final PaymentRepository paymentRepository;
  private final QrService qrService;
  private final Clock clock; //수정 : 시간 기준을 Clock 으로 통일 (한국 시간, 테스트에서 고정 가능)

  // Transaction 1. 선점(claim) : (walletId, 멱등키) 복합 유니크 제약으로 중복 요청을 걸러낸다
  @Transactional
  public PaymentResponse claim(String key, PaymentRequest request) {
    QrPaymentTarget target = qrService.getPaymentTarget(request.qrToken());
    if(request.cashbackUsed() > target.amount()){
      throw new BusinessException(INVALID_REQUEST,"캐시백 사용 금액은 결제 금액을 넘을 수 없습니다");
    }
    Payment payment =
        Payment.builder()
            .walletId(request.walletId())
            .qrToken(request.qrToken())
            .idempotencyKey(key)
            .cashbackUsedAmount(request.cashbackUsed())
            .merchantId(target.merchantId())
            .amount(target.amount())
            .walletUsedAmount(target.amount()-request.cashbackUsed())
            .status(PaymentStatus.PROCESSING)
            .requestedAt(LocalDateTime.now(clock)) // 서버 최초 접수 시각 //수정 : Clock 사용
            .build();
    return PaymentResponse.of(paymentRepository.saveAndFlush(payment));
  }

  // 같은 멱등키로 들어온 요청 체크 (읽기전용)
  @Transactional(readOnly = true)
  public PaymentResponse findExistingResult(String key, PaymentRequest request) {
    Payment existing =
        paymentRepository
            .findByWalletIdAndIdempotencyKey(request.walletId(), key)
            .orElseThrow(() -> new BusinessException(IDEMPOTENCY_IN_PROGRESS));

    // 1. 같은 키인데 요청 내용이 다름
    if (!isSameRequest(existing, request)) {
      throw new BusinessException(IDEMPOTENCY_KEY_MISMATCH);
    }
    // 2. 진행 중
    if (existing.getStatus() == PaymentStatus.PROCESSING) {
      throw new BusinessException(IDEMPOTENCY_IN_PROGRESS);
    }
    // 3. 완료 / 취소 / 실패: 저장된 결과를 그대로 반환 (실패 건도 같은 키로는 재시도하지 않는다)
    return PaymentResponse.of(existing);
  }

  private boolean isSameRequest(Payment p, PaymentRequest r) {
    return  p.getCashbackUsedAmount().equals(r.cashbackUsed())
            && Objects.equals(p.getQrToken(), r.qrToken()); //null일수도 있어서 object로비교
  }
}
