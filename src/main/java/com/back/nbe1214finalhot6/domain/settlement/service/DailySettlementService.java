package com.back.nbe1214finalhot6.domain.settlement.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MerchantSalesRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 하루치 정산 대상을 모아 가맹점마다 처리한다. 가맹점 한 곳의 실패가 다른 곳에 영향을 주지 않도록 트랜잭션은 열지 않는다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySettlementService {

  private static final List<PaymentStatus> COMPLETED_STATUSES =
      List.of(PaymentStatus.COMPLETED, PaymentStatus.CANCELED);

  private final MerchantSalesRepository merchantSalesRepository;
  private final DailySettlementRepository settlementRepository;
  private final DailySettlementProcessor processor;
  private final Clock clock;

  /**
   * 가장 최근에 정산된 날짜부터 lastDate 까지 날짜 순서대로 정산한다. 서버가 꺼져 있어 건너뛴 날도 이어서 채워야 전일 이월이 끊기지 않는다.
   * 정산 이력이 없으면 lastDate 하루만 정산한다. 실패한 가맹점이 있는 날짜에서 멈추고, 다음 실행이 그 날짜부터 다시 시작한다.
   */
  public List<SettlementRunResult> settleUntil(LocalDate lastDate) {
    LocalDate from =
        settlementRepository
            .findLatestSettlementDate()
            .filter(latest -> !latest.isAfter(lastDate))
            .orElse(lastDate);

    List<SettlementRunResult> results = new ArrayList<>();
    for (LocalDate date = from; !date.isAfter(lastDate); date = date.plusDays(1)) {
      SettlementRunResult result = settle(date);
      results.add(result);
      if (result.failed() > 0) {
        break;
      }
    }
    return results;
  }

  public SettlementRunResult settle(LocalDate date) {
    if (!date.isBefore(LocalDate.now(clock))) {
      throw new BusinessException(SettlementErrorCode.SETTLEMENT_DATE_NOT_ALLOWED);
    }

    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    Map<Long, Long> payments =
        toMap(merchantSalesRepository.sumByCompletedAt(start, end, COMPLETED_STATUSES));
    Map<Long, Long> cancels =
        toMap(merchantSalesRepository.sumByCanceledAt(start, end, PaymentStatus.CANCELED));

    Set<Long> targets = new TreeSet<>();
    targets.addAll(payments.keySet());
    targets.addAll(cancels.keySet());
    settlementRepository
        .findBySettlementDateAndCarryOutLessThan(date.minusDays(1), 0L)
        .forEach(previous -> targets.add(previous.getMerchantId()));

    int created = 0;
    int skipped = 0;
    int failed = 0;
    for (Long merchantId : targets) {
      try {
        boolean isNew =
            processor.settleOne(
                merchantId,
                date,
                payments.getOrDefault(merchantId, 0L),
                cancels.getOrDefault(merchantId, 0L));
        if (isNew) {
          created++;
        } else {
          skipped++;
        }
      } catch (DataIntegrityViolationException e) {
        // 동시에 실행되어 이미 저장된 경우만 건너뛰고, 그 밖의 무결성 오류는 실패로 센다
        if (settlementRepository.existsByMerchantIdAndSettlementDate(merchantId, date)) {
          skipped++;
        } else {
          failed++;
          log.error("일 정산 무결성 오류 merchantId={}, date={}", merchantId, date, e);
        }
      } catch (RuntimeException e) {
        failed++;
        log.error("일 정산 실패 merchantId={}, date={}", merchantId, date, e);
      }
    }
    return new SettlementRunResult(date, created, skipped, failed);
  }

  private Map<Long, Long> toMap(List<MerchantSales> sales) {
    Map<Long, Long> result = new HashMap<>();
    sales.forEach(sale -> result.put(sale.merchantId(), sale.total()));
    return result;
  }
}
