package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackNotice;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackNoticeRepository extends JpaRepository<CashbackNotice, Long> {

  /** 해당 상태의 공지. 정렬과 페이지는 호출한 쪽이 정한다. */
  Page<CashbackNotice> findByStatus(NoticeStatus status, Pageable pageable);

  /** 해당 상태의 한 종류 공지. 정렬과 페이지는 호출한 쪽이 정한다. */
  Page<CashbackNotice> findByTypeAndStatus(NoticeType type, NoticeStatus status, Pageable pageable);

  /** 정책(요율 구간)을 알린 공지. 정책마다 종류별로 한 건이다. */
  Optional<CashbackNotice> findByPolicyIdAndType(Long policyId, NoticeType type);

  /** 홈에 띄울 공지. 종료일이 today 이후(포함)인 것 중 최근 등록순 5건이다. */
  List<CashbackNotice> findTop5ByStatusAndShowOnHomeTrueAndHomeUntilGreaterThanEqualOrderByCreatedAtDescIdDesc(
      NoticeStatus status, LocalDate today);
}
