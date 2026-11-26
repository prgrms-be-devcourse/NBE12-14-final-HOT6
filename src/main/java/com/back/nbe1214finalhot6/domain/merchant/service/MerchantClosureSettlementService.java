package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.domain.merchant.dto.ClosureSettlementRunResult;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantClosureRepository;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.service.DailySettlementService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 해지를 신청한(CLOSING) 가맹점을 모아 한 곳씩 정산하고 해지를 끝낸다. 한 곳의 실패가 다른 곳에 영향을 주지 않도록 트랜잭션은 열지 않는다.
 *
 * <p>신청한 날 전체를 정산하므로, 신청일이 지난 뒤(다음 날부터)에 처리한다. 신청일까지의 일 정산이 만들어지지 않았으면 매출이 빠진 채 해지될 수 있어 미룬다. 같은
 * 가맹점을 다시 실행해도 이미 끝난 곳은 CLOSING 이 아니라서 다시 처리되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantClosureSettlementService {

  private final MerchantRepository merchantRepository;
  private final MerchantClosureRepository closureRepository;
  private final DailySettlementRepository dailySettlementRepository;
  private final DailySettlementService dailySettlementService;
  private final MerchantClosureSettlementProcessor processor;
  private final Clock clock;

  public ClosureSettlementRunResult run() {
    List<Merchant> closing = merchantRepository.findByStatusOrderByIdAsc(MerchantStatus.CLOSING);
    if (closing.isEmpty()) {
      return new ClosureSettlementRunResult(0, 0, 0, 0, 0, List.of());
    }

    LocalDate today = LocalDate.now(clock);
    LocalDate settledThrough = settleDailyUntilYesterday(today);
    Optional<LocalDate> earliestDaily = dailySettlementRepository.findEarliestSettlementDate();

    int closed = 0;
    int held = 0;
    int waiting = 0;
    int dailyIncomplete = 0;
    int failed = 0;
    List<Long> heldIds = new ArrayList<>();
    for (Merchant merchant : closing) {
      Long merchantId = merchant.getId();
      try {
        LocalDate closureDate =
            closureRepository
                .findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalStateException("해지 신청 기록이 없습니다"))
                .getRequestedAt()
                .toLocalDate();
        if (!closureDate.isBefore(today)) {
          waiting++;
          continue;
        }
        // 일 정산이 신청일까지 이어지지 않았거나, 신청일이 일 정산이 시작된 날보다 앞이면 매출이 비어 있을 수 있다
        boolean dailyReady =
            !closureDate.isAfter(settledThrough)
                && earliestDaily.map(earliest -> !earliest.isAfter(closureDate)).orElse(false);
        if (!dailyReady) {
          dailyIncomplete++;
          continue;
        }
        switch (processor.settleOne(merchantId, closureDate)) {
          case CLOSED -> closed++;
          case HELD -> {
            held++;
            heldIds.add(merchantId);
          }
          case ALREADY -> {}
        }
      } catch (RuntimeException e) {
        failed++;
        log.error("해지 정산 실패 merchantId={}", merchantId, e);
      }
    }
    return new ClosureSettlementRunResult(closed, held, waiting, dailyIncomplete, failed, heldIds);
  }

  // 어제까지 일 정산을 채우고, 실패한 날이 있으면 그 전날까지만 정산된 것으로 본다
  private LocalDate settleDailyUntilYesterday(LocalDate today) {
    List<SettlementRunResult> results = dailySettlementService.settleUntil(today.minusDays(1));
    SettlementRunResult last = results.getLast();
    return last.failed() > 0 ? last.date().minusDays(1) : last.date();
  }
}
