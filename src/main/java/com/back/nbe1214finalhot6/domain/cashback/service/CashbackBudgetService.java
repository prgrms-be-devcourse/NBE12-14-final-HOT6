package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackAnnualBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 캐시백 예산 관리. */
@Service
@RequiredArgsConstructor
public class CashbackBudgetService {

  // 적립을 받는 상태. 중단·마감 달에는 예산을 확보하지 않는다
  private static final Set<BudgetStatus> RESERVABLE_STATUSES =
      EnumSet.of(BudgetStatus.ACTIVE, BudgetStatus.NOTICED);

  private final CashbackAnnualBudgetRepository annualBudgetRepository;
  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackProperties properties;
  private final CashbackBudgetStateService stateService;
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
   * @throws BusinessException 월·금액 오류나 지난 달(CSH-007), 요율 오류나 이어받을 값 없음(CSH-003), 연 예산 없음(CSH-004),
   *     합계 초과(CSH-005), 예약·지급이 시작된 달(CSH-008)
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

  /**
   * 결제가 적립액만큼 예산을 확보한다. 캐시백 도메인 내부 전용이며, 결제 도메인은 {@code CashbackService.reserve} 만
   * 호출한다.
   *
   * <p>연 예산, 월 예산 순서로 조건부 UPDATE 를 실행하며, 한 문장 안에서 남은 예산을 확인하고 늘리므로 동시에 확보해도 예산을 넘지 않는다.
   * 호출한 쪽의 트랜잭션에 참여한다. 확보가 성공하면 직후에 적립 중단 상태를 판정한다({@link CashbackBudgetStateService}).
   *
   * <p>영속성 컨텍스트를 비우지 않으므로 결제 트랜잭션이 읽어 둔 엔티티가 분리되지 않는다. 같은 트랜잭션에서 예산 엔티티를 미리 읽어 두면 갱신 전 값일 수
   * 있어서, 확보 결과는 반환값으로 판단한다.
   *
   * <p>예산이 모자라거나 없거나 적립이 중단된 것은 예외가 아니라 결과로 돌려준다(결제는 그대로 진행된다). 부분 확보는 없고 전액이거나 아무것도 확보하지
   * 않는다. 월 예산에서 실패하면 먼저 늘린 연 예산 확보를 같은 트랜잭션에서 되돌린다. 연 예산 행의 잠금을 쥐고 있어 되돌리는 사이에 다른 결제가 끼어들지
   * 못한다.
   *
   * @throws BusinessException 금액이 0 이하이거나 월이 1~12 가 아닐 때(CSH-007)
   */
  @Transactional
  public BudgetReserveResult reserveBudget(int year, int month, long amount) {
    validateReservation(month, amount);
    if (annualBudgetRepository.reserve(year, amount) == 0) {
      return annualBudgetRepository.existsByBudgetYear(year)
          ? BudgetReserveResult.INSUFFICIENT
          : BudgetReserveResult.NO_BUDGET;
    }
    if (monthlyBudgetRepository.reserve(year, month, amount, RESERVABLE_STATUSES) == 0) {
      annualBudgetRepository.release(year, amount);
      return monthlyFailure(year, month);
    }
    // 확보한 직후, 같은 트랜잭션에서 남은 예산이 공지선·긴급선 아래로 내려갔는지 판정한다
    stateService.evaluateAfterReserve(year, month);
    return BudgetReserveResult.RESERVED;
  }

  /**
   * 확보했던 예산을 되돌린다. 캐시백 도메인 내부 전용이며, 결제 도메인은 {@code CashbackService.cancel} 만
   * 호출한다. 호출한 쪽의 트랜잭션에 참여하고, 연 예산, 월 예산 순서로 되돌린다.
   *
   * @throws BusinessException 금액이 0 이하이거나 월이 1~12 가 아닐 때, 확보한 금액보다 많이 되돌리려 할 때(CSH-007). 예외가 나면 이미
   *     되돌린 부분도 함께 롤백된다
   */
  @Transactional
  public void releaseBudget(int year, int month, long amount) {
    validateReservation(month, amount);
    if (annualBudgetRepository.release(year, amount) == 0
        || monthlyBudgetRepository.release(year, month, amount) == 0) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_BUDGET, "확보한 금액보다 많이 되돌릴 수 없습니다");
    }
  }

  private static void validateReservation(int month, long amount) {
    CashbackMonthlyBudget.validateMonth(month);
    if (amount <= 0) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "금액은 0보다 커야 합니다");
    }
  }

  /** 월 예산 확보가 실패한 이유. 월 예산이 없으면 없음, 적립을 받지 않는 상태면 중단, 그 밖에는 예산 부족이다. */
  private BudgetReserveResult monthlyFailure(int year, int month) {
    return monthlyBudgetRepository
        .findStatus(year, month)
        .map(
            status ->
                RESERVABLE_STATUSES.contains(status)
                    ? BudgetReserveResult.INSUFFICIENT
                    : BudgetReserveResult.NOT_ACTIVE)
        .orElse(BudgetReserveResult.NO_BUDGET);
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
