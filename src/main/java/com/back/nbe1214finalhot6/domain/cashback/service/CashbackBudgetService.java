package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 캐시백 예산 관리. */
@Service
@RequiredArgsConstructor
public class CashbackBudgetService {

  private final CashbackAnnualBudgetRepository annualBudgetRepository;
  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackProperties properties;
  private final Clock clock;

  /**
   * 연 예산을 등록하거나 총액을 바꾼다. 같은 연도는 한 건만 존재한다.
   *
   * <p>같은 연도를 동시에 처음 등록하면 한쪽은 유니크 제약으로 실패한다. 운영자 단독 작업을 전제로 따로 처리하지 않는다. 이미 있는 연도는 행을
   * 잠그고 읽어서, 총액을 바꾸는 사이 커밋된 예약·지급 금액을 낡은 값으로 덮어쓰지 않고 월 배정과도 동시에 바뀌지 않게 한다.
   *
   * @throws BusinessException 총액이 월 배정 합계보다 작을 때(CSH-005)
   */
  @Transactional
  public AnnualBudgetResponse putAnnual(int year, AnnualBudgetRequest request) {
    CashbackAnnualBudget budget =
        annualBudgetRepository.findWithLockByBudgetYear(year).orElse(null);
    if (budget == null) {
      budget =
          annualBudgetRepository.save(CashbackAnnualBudget.create(year, request.totalAmount()));
    } else {
      budget.changeTotal(request.totalAmount(), monthlyBudgetRepository.sumAllocated(year));
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

  /**
   * 월 예산을 배정한다. 같은 연·월은 한 건이며, 있으면 배정을 바꾸고 대비금을 다시 확정한다.
   *
   * <p>연 예산 행을 잠근 채 합계를 검증하므로 동시에 배정해도 합계가 연 예산을 넘지 않는다. 이어서 해당 월 예산 행도 잠가서, 그 사이 커밋된
   * 예약 금액을 낡은 값으로 덮어쓰지 않고 사용 여부도 최신 값으로 판단한다. 잠금 순서는 연 -> 월이다.
   *
   * <p>기본 요율은 선택 입력이다. 새로 만드는 달에서 비우면 이전 달 값을 이어받는다.
   *
   * @throws BusinessException 월·금액 오류나 지난 달(CSH-007), 요율 오류나 이어받을 값 없음(CSH-003), 연 예산 없음(CSH-004), 합계
   *     초과(CSH-005), 예약·지급이 시작된 달(CSH-008)
   */
  @Transactional
  public MonthlyBudgetResponse putMonthly(int year, int month, MonthlyBudgetRequest request) {
    CashbackMonthlyBudget.validateMonth(month);
    if (YearMonth.of(year, month).isBefore(YearMonth.now(clock))) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "지난 달은 배정할 수 없습니다");
    }
    CashbackAnnualBudget annual =
        annualBudgetRepository
            .findWithLockByBudgetYear(year)
            .orElseThrow(
                () ->
                    new BusinessException(
                        CashbackErrorCode.BUDGET_NOT_FOUND, "해당 연도의 예산을 찾을 수 없습니다"));
    // 덧셈은 long 범위를 넘을 수 있어, 남은 한도와 비교한다(합계 ≤ 연 총액이므로 뺄셈은 안전).
    long remainingCapacity =
        annual.getTotalAmount() - monthlyBudgetRepository.sumAllocatedExcluding(year, month);
    if (request.allocatedAmount() > remainingCapacity) {
      throw new BusinessException(CashbackErrorCode.BUDGET_ALLOCATION_EXCEEDED);
    }

    CashbackMonthlyBudget budget =
        monthlyBudgetRepository
            .findWithLockByBudgetYearAndBudgetMonth(year, month)
            .orElse(null);
    if (budget == null) {
      budget =
          monthlyBudgetRepository.save(
              CashbackMonthlyBudget.create(
                  year,
                  month,
                  request.allocatedAmount(),
                  properties.contingencyRate(),
                  baseRateForNewMonth(year, month, request.baseRate())));
    } else {
      budget.changeAllocated(
          request.allocatedAmount(), properties.contingencyRate(), request.baseRate());
    }
    return MonthlyBudgetResponse.from(budget);
  }

  /** 새로 만드는 달의 기본 요율. 입력이 없으면 가장 가까운 이전 달의 값을 이어받고, 이어받을 값이 없으면 거절한다. */
  private BigDecimal baseRateForNewMonth(int year, int month, BigDecimal requested) {
    if (requested != null) {
      return requested;
    }
    return monthlyBudgetRepository
        .findLatestBaseRateBefore(year, month)
        .map(CashbackMonthlyBudget::getBaseRate)
        .orElseThrow(
            () ->
                new BusinessException(
                    CashbackErrorCode.INVALID_POLICY, "이어받을 이전 달의 기본 요율이 없어 입력해야 합니다"));
  }

  @Transactional(readOnly = true)
  public MonthlyBudgetResponse getMonthly(int year, int month) {
    return monthlyBudgetRepository
        .findByBudgetYearAndBudgetMonth(year, month)
        .map(MonthlyBudgetResponse::from)
        .orElseThrow(() -> new BusinessException(CashbackErrorCode.BUDGET_NOT_FOUND));
  }

  /** 해당 연도에 배정된 월 예산을 월 순서로 돌려준다. 배정하지 않은 달은 없다. */
  @Transactional(readOnly = true)
  public List<MonthlyBudgetResponse> listMonthly(int year) {
    return monthlyBudgetRepository.findAllByBudgetYearOrderByBudgetMonth(year).stream()
        .map(MonthlyBudgetResponse::from)
        .toList();
  }
}
