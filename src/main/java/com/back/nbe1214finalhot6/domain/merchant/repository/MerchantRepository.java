package com.back.nbe1214finalhot6.domain.merchant.repository;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

  // 이름 검색은 영업 중인 가맹점만 보여 준다
  Page<Merchant> findByStatusAndNameContainingIgnoreCase(
      MerchantStatus status, String name, Pageable pageable);

  // 해당 상태의 가맹점을 오래된 순으로 읽는다. 해지 정산이 CLOSING 가맹점을 모을 때 쓴다
  List<Merchant> findByStatusOrderByIdAsc(MerchantStatus status);

  // 점주의 가맹점 조회. 점주당 하나만 허용(유일 제약)
  Optional<Merchant> findByOwnerId(Long ownerId);

  boolean existsByOwnerId(Long ownerId);

  // 현재 상태가 from 일 때만 to 로 바꾼다. 해지 신청이 동시에 와도 한 번만 반영된다
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Merchant m set m.status = :to where m.id = :id and m.status = :from")
  int updateStatusIfCurrent(
      @Param("id") Long id, @Param("from") MerchantStatus from, @Param("to") MerchantStatus to);

  // 상태만 읽는다. 호출한 트랜잭션에 남은 이전 엔티티 값을 읽지 않기 위해 엔티티 대신 값만 조회한다
  @Query("select m.status from Merchant m where m.id = :id")
  Optional<MerchantStatus> findStatusById(@Param("id") Long id);
}
