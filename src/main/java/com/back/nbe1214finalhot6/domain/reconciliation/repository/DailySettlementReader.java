package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 대사가 일 정산 장부를 읽기만 하는 저장소. 양 끝 날짜를 포함한다. */
public interface DailySettlementReader extends Repository<DailySettlement, Long> {

  List<DailySettlement> findBySettlementDate(LocalDate date);

  List<DailySettlement> findBySettlementDateBetween(LocalDate from, LocalDate to);
}
