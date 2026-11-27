package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackForecastSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackForecastSnapshotRepository
    extends JpaRepository<CashbackForecastSnapshot, Long> {

  boolean existsBySnapshotDate(LocalDate snapshotDate);

  /** {@code from} 이상 {@code before} 미만 날짜의 예측. 최근 날짜부터. */
  List<CashbackForecastSnapshot>
      findBySnapshotDateGreaterThanEqualAndSnapshotDateLessThanOrderBySnapshotDateDesc(
          LocalDate from, LocalDate before);
}
