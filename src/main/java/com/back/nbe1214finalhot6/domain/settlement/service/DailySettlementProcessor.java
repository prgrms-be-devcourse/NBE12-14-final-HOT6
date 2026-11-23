package com.back.nbe1214finalhot6.domain.settlement.service;

import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가맹점 한 곳의 하루 정산을 한 트랜잭션으로 처리한다. 호출하는 쪽은 트랜잭션을 열지 않는다. */
@Service
@RequiredArgsConstructor
public class DailySettlementProcessor {

  private final DailySettlementRepository settlementRepository;
  private final Clock clock;

  /** 새로 만들었으면 true, 이미 정산된 날짜여서 넘겼으면 false 를 돌려준다. */
  @Transactional
  public boolean settleOne(Long merchantId, LocalDate date, long paymentTotal, long cancelTotal) {
    if (settlementRepository.existsByMerchantIdAndSettlementDate(merchantId, date)) {
      return false;
    }
    long carryIn =
        settlementRepository
            .findByMerchantIdAndSettlementDate(merchantId, date.minusDays(1))
            .map(DailySettlement::getCarryOut)
            .orElse(0L);
    settlementRepository.save(
        DailySettlement.create(
            merchantId, date, paymentTotal, cancelTotal, carryIn, LocalDateTime.now(clock)));
    return true;
  }
}
