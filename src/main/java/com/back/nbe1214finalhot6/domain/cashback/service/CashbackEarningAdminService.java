package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.AdminEarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 적립 내역 전체 조회. 읽기만 한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashbackEarningAdminService {

  static final int MAX_PAGE_SIZE = 100;

  private final CashbackEarningRepository earningRepository;

  /**
   * 모든 이용자의 적립 내역을 최근 접수순으로 돌려준다. 조건은 준 것만 함께 적용한다.
   *
   * @param status 상태. null 이면 전체
   * @param walletId 지갑 번호. null 이면 전체
   * @param from 결제 접수일의 시작(그날 포함). null 이면 제한 없음
   * @param to 결제 접수일의 끝(그날 포함). null 이면 제한 없음
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   * @throws BusinessException from 이 to 보다 늦을 때(CSH-016)
   */
  public PageResponse<AdminEarningResponse> search(
      EarningStatus status, Long walletId, LocalDate from, LocalDate to, int page, int size) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new BusinessException(CashbackErrorCode.INVALID_SEARCH_PERIOD);
    }
    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id")));
    return PageResponse.from(
        earningRepository
            .findAll(matching(status, walletId, from, to), pageable)
            .map(AdminEarningResponse::from));
  }

  private static Specification<CashbackEarning> matching(
      EarningStatus status, Long walletId, LocalDate from, LocalDate to) {
    return (root, query, cb) -> {
      List<Predicate> conditions = new ArrayList<>();
      if (status != null) {
        conditions.add(cb.equal(root.get("status"), status));
      }
      if (walletId != null) {
        conditions.add(cb.equal(root.get("walletId"), walletId));
      }
      if (from != null) {
        conditions.add(cb.greaterThanOrEqualTo(root.get("requestedAt"), from.atStartOfDay()));
      }
      if (to != null) {
        conditions.add(cb.lessThan(root.get("requestedAt"), to.plusDays(1).atStartOfDay()));
      }
      return cb.and(conditions.toArray(Predicate[]::new));
    };
  }
}
