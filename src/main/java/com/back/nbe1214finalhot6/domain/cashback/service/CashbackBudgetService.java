package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 캐시백 예산 관리. */
@Service
@RequiredArgsConstructor
public class CashbackBudgetService {

  private final CashbackAnnualBudgetRepository annualBudgetRepository;

  /**
   * 연 예산을 등록하거나 총액을 바꾼다. 같은 연도는 한 건만 존재한다.
   *
   * <p>같은 연도를 동시에 처음 등록하면 한쪽은 유니크 제약으로 실패한다. 운영자 단독 작업을 전제로 따로 처리하지 않는다.
   */
  @Transactional
  public AnnualBudgetResponse putAnnual(int year, AnnualBudgetRequest request) {
    CashbackAnnualBudget budget = annualBudgetRepository.findByBudgetYear(year).orElse(null);
    if (budget == null) {
      budget = annualBudgetRepository.save(CashbackAnnualBudget.create(year, request.totalAmount()));
    } else {
      budget.changeTotal(request.totalAmount());
    }
    return AnnualBudgetResponse.from(budget);
  }

  @Transactional(readOnly = true)
  public AnnualBudgetResponse getAnnual(int year) {
    return annualBudgetRepository
        .findByBudgetYear(year)
        .map(AnnualBudgetResponse::from)
        .orElseThrow(
            () ->
                new BusinessException(
                    CashbackErrorCode.BUDGET_NOT_FOUND, "해당 연도의 예산을 찾을 수 없습니다"));
  }
}
