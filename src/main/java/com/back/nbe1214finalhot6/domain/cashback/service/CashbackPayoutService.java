package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 캐시백 적립 지급 배치. 지급 예정 시각이 지난 적립 예정 건을 건별 트랜잭션으로 지갑에 입금한다.
 *
 * <p>이 클래스는 트랜잭션 없이 대상 건 ID 만 고르고, 건마다 {@link CashbackPayoutProcessor} 를 호출한다. 한 건이 실패해도 그 건만 롤백되어 적립
 * 예정으로 남고 다음 배치에서 다시 시도되며, 나머지 건은 계속 처리한다. 스케줄러와 운영자 수동 실행이 겹쳐 돌아도 건별 처리가 한 번만 지급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashbackPayoutService {

  private final CashbackEarningRepository earningRepository;
  private final CashbackPayoutProcessor processor;
  private final CashbackProperties properties;
  private final Clock clock;

  /** 지급할 수 있는 건을 모두 처리한다. */
  public PayoutRunResponse run() {
    return run(properties.scheduler().payoutBatchSize());
  }

  /**
   * 지급할 수 있는 건을 {@code batchSize} 건씩 읽어 모두 처리한다. 읽은 시각 이후에 지급 예정 시각이 지나는 건은 다음 배치가 처리한다. 마지막으로
   * 처리한 ID 부터 이어서 읽으므로 실패한 건이 같은 배치에서 다시 잡히지 않는다.
   */
  PayoutRunResponse run(int batchSize) {
    LocalDateTime now = LocalDateTime.now(clock);
    int paid = 0;
    int skipped = 0;
    int failed = 0;
    long afterId = 0L;
    while (true) {
      List<Long> ids =
          earningRepository.findPayableIds(
              now, afterId, EarningStatus.PENDING, PageRequest.of(0, batchSize));
      if (ids.isEmpty()) {
        break;
      }
      for (Long id : ids) {
        afterId = id;
        try {
          if (processor.pay(id) == PayoutOutcome.PAID) {
            paid++;
          } else {
            skipped++;
          }
        } catch (RuntimeException e) {
          failed++;
          log.warn("캐시백 지급 실패 earningId={}: {}", id, e.getMessage());
        }
      }
    }
    return new PayoutRunResponse(paid, skipped, failed);
  }
}
