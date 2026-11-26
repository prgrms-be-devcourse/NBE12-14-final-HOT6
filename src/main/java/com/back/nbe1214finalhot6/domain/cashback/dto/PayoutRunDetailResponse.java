package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPayoutRunItem;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutItemResult;
import java.util.List;

/**
 * 지급 배치 실행 한 번의 상세.
 *
 * @param run 실행 시각·방식·건수
 * @param paidAmount 이 실행에서 지급한 금액 합계(원)
 * @param items 처리한 적립 건. 실패, 넘어감, 지급 순이며 같은 결과 안에서는 적립 건 순이다
 */
public record PayoutRunDetailResponse(
    PayoutRunSummaryResponse run, long paidAmount, List<Item> items) {

  public record Item(
      Long earningId,
      Long paymentId,
      Long walletId,
      long amount,
      PayoutItemResult result,
      String errorCode,
      String errorMessage) {

    public static Item from(CashbackPayoutRunItem item) {
      return new Item(
          item.getEarningId(),
          item.getPaymentId(),
          item.getWalletId(),
          item.getAmount(),
          item.getResult(),
          item.getErrorCode(),
          item.getErrorMessage());
    }
  }
}
