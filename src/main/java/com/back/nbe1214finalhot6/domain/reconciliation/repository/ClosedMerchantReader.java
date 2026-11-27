package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantClosure;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 대사가 해지 완료 가맹점과 그 해지 신청 기록을 읽기만 하는 저장소. */
public interface ClosedMerchantReader extends Repository<MerchantClosure, Long> {

  // 해지 완료(CLOSED) 가맹점의 번호. 신청 기록이 없는 가맹점도 빠뜨리지 않으려고 가맹점 쪽에서 읽는다
  @Query(
      "select m.id from Merchant m "
          + "where m.status = com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus.CLOSED "
          + "order by m.id")
  List<Long> findClosedMerchantIds();

  // 해지 신청 기록. 신청일까지가 해지 정산 대상이다
  @Query("select c from MerchantClosure c where c.merchantId in :merchantIds")
  List<MerchantClosure> findClosures(@Param("merchantIds") Collection<Long> merchantIds);
}
