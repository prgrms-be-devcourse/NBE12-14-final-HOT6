package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.Refund;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 대사가 환불 기록을 읽기만 하는 저장소. 시작은 포함하고 끝은 포함하지 않는다. */
public interface RefundReader extends Repository<Refund, Long> {

  List<Refund> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(
      LocalDateTime start, LocalDateTime end);
}
