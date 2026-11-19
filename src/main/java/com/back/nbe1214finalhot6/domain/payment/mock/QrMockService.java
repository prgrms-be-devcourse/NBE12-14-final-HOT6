package com.back.nbe1214finalhot6.domain.payment.mock;

import org.springframework.stereotype.Service;

// TODO: D의 QrService 연동 후 삭제 (PaymentTxService 의 주입 타입을 QrService 로 교체)
@Service
public class QrMockService {

  // QR을 ACTIVE → USED로 조건부 변경. (실제 구현은 이미 사용됐거나 만료면 BusinessException)
  public void markUsed(Long qrId) {
    // 가짜 구현: 아무것도 하지 않음 (항상 성공)
  }
}
