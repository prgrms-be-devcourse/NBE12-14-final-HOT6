package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.reconciliation.entity.ReconciliationResult;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, Long> {

  List<ReconciliationResult> findByTargetDateBetweenOrderByIdDesc(LocalDate from, LocalDate to);
}
