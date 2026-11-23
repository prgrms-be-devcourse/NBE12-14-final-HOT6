package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackNotice;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackNoticeRepository extends JpaRepository<CashbackNotice, Long> {

  /** 한 종류의 공지. 정렬과 페이지는 호출한 쪽이 정한다. */
  Page<CashbackNotice> findByType(NoticeType type, Pageable pageable);
}
