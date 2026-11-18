package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackExclusionReason;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackReserveResponse;
import org.springframework.stereotype.Service;

/**
 * 결제 도메인이 쓰는 캐시백 창구. 결제 트랜잭션 안에서 호출하며, 결제가 롤백되면 예산 확보도 함께 롤백된다.
 *
 * <p>지금은 임시로 항상 적립 0원을 돌려준다. 결제 도메인이 먼저 연동할 수 있게 둔 것이며, 정책·예산·적립 구현이 들어오면 이 클래스 안을
 * 채운다. 메서드 시그니처와 반환 타입은 바꾸지 않는다.
 */
@Service
public class CashbackService {

  /**
   * 적립액을 계산하고 그만큼의 예산과 이용자 월 한도를 확보한다.
   *
   * <p>적립은 전액 또는 0원이다. 예산이 부족하거나 한도를 넘으면 예외 없이 0원과 사유를 돌려주며, 결제는 그대로 진행한다.
   */
  public CashbackReserveResponse reserve(CashbackReserveRequest command) {
    return CashbackReserveResponse.excluded(CashbackExclusionReason.NOT_ELIGIBLE);
  }

  /**
   * 결제 취소에 따라 적립을 되돌린다. 지급 전이면 확보한 예산을 반환하고, 지급 후면 회수한다.
   *
   * <p>같은 결제를 여러 번 취소해도 한 번만 처리한다.
   */
  public void cancel(Long paymentId) {
    // 아직 확보한 것이 없으므로 되돌릴 것도 없다.
  }
}
