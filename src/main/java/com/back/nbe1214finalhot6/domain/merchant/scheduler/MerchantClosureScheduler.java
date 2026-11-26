package com.back.nbe1214finalhot6.domain.merchant.scheduler;

import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureSettlementRunResult;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantClosureSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantClosureScheduler {

  private final MerchantClosureSettlementService settlementService;

  // 일 정산(00:10)과 월 정산(00:30) 뒤에 돌린다. 이미 끝난 가맹점은 다시 처리되지 않는다
  @Scheduled(cron = "0 40 0 * * *", zone = "Asia/Seoul")
  public void settleClosures() {
    ClosureSettlementRunResult result = settlementService.run();
    if (result.failed() > 0 || result.held() > 0 || result.dailyIncomplete() > 0) {
      log.warn(
          "해지 정산 일부 미처리 closed={}, held={}, waiting={}, dailyIncomplete={}, failed={}, heldMerchantIds={}",
          result.closed(),
          result.held(),
          result.waiting(),
          result.dailyIncomplete(),
          result.failed(),
          result.heldMerchantIds());
    } else {
      log.info("해지 정산 완료 closed={}, waiting={}", result.closed(), result.waiting());
    }
  }
}
