package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.Charge;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 대사가 충전 기록을 읽기만 하는 저장소. 시작은 포함하고 끝은 포함하지 않는다. */
public interface ChargeReader extends Repository<Charge, Long> {

  List<Charge> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(
      LocalDateTime start, LocalDateTime end);
}
