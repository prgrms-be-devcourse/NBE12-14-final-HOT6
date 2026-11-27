package com.back.nbe1214finalhot6.domain.settlement.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlementStatus;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.repository.MonthlySettlementRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 한 달치 일 정산 확정액을 가맹점별로 합산해 점주의 모의계좌로 지급한다. 가맹점 한 곳의 실패가 다른 곳에 영향을 주지 않도록 트랜잭션은 열지 않고, 건마다
 * 지급을 따로 커밋한다. 지급이 실패하면 건은 대기로 남고 다음 실행에서 다시 지급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlySettlementService {

  private final DailySettlementRepository dailySettlementRepository;
  private final MonthlySettlementRepository monthlySettlementRepository;
  private final MonthlySettlementProcessor processor;
  private final Clock clock;

  public MonthlySettlementRunResult settle(YearMonth month) {
    MonthlySettlementRunResult creation = createMissing(month);
    MonthlySettlementRunResult payment = payPending(month);
    return new MonthlySettlementRunResult(
        month, creation.created(), payment.paid(), creation.failed() + payment.failed());
  }

  /** 해당 월의 일 정산이 모두 끝난 뒤에만 호출한다. 건은 한 번 만들면 금액을 고치지 않는다. */
  public MonthlySettlementRunResult createMissing(YearMonth month) {
    if (!month.isBefore(YearMonth.now(clock))) {
      throw new BusinessException(SettlementErrorCode.SETTLEMENT_MONTH_NOT_ALLOWED);
    }

    LocalDate monthStart = month.atDay(1);
    int created = 0;
    int failed = 0;
    for (MerchantSales sales :
        dailySettlementRepository.sumConfirmedAmountBetween(
            monthStart, month.plusMonths(1).atDay(1))) {
      if (sales.total() <= 0) {
        continue;
      }
      try {
        if (processor.createIfAbsent(sales.merchantId(), monthStart, sales.total())) {
          created++;
        }
      } catch (DataIntegrityViolationException e) {
        // 동시에 실행되어 이미 저장된 경우만 건너뛰고, 그 밖의 무결성 오류는 실패로 센다
        if (!monthlySettlementRepository.existsByMerchantIdAndSettlementMonth(
            sales.merchantId(), monthStart)) {
          failed++;
          log.error("월 정산 건 생성 오류 merchantId={}, month={}", sales.merchantId(), month, e);
        }
      } catch (RuntimeException e) {
        failed++;
        log.error("월 정산 건 생성 실패 merchantId={}, month={}", sales.merchantId(), month, e);
      }
    }
    return new MonthlySettlementRunResult(month, created, 0, failed);
  }

  /** 달에 상관없이 지급이 끝나지 않은 건을 모두 지급한다. 일 정산 상태와 무관하다. */
  public MonthlySettlementRunResult payPending(YearMonth month) {
    int paid = 0;
    int failed = 0;
    for (MonthlySettlement pending :
        monthlySettlementRepository.findByStatusOrderByIdAsc(MonthlySettlementStatus.PENDING)) {
      try {
        pay(pending);
        paid++;
      } catch (RuntimeException e) {
        failed++;
        log.error(
            "월 정산 지급 실패 settlementId={}, merchantId={}",
            pending.getId(),
            pending.getMerchantId(),
            e);
      }
    }
    return new MonthlySettlementRunResult(month, 0, paid, failed);
  }

  private void pay(MonthlySettlement settlement) {
    processor.payAndComplete(settlement.getId());
  }
}
