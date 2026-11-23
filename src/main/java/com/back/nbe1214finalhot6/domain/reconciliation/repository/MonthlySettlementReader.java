package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.settlement.entity.MonthlySettlement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 대사가 월 정산 건을 읽기만 하는 저장소. */
public interface MonthlySettlementReader extends Repository<MonthlySettlement, Long> {

  List<MonthlySettlement> findBySettlementMonth(LocalDate settlementMonth);
}
