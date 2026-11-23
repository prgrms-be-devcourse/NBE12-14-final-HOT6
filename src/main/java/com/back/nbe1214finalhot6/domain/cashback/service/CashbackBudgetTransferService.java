package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetTransfer;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetTransferRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월 예산 이월. 한 달의 남은 예산을 다음 달로 넘긴다. 운영자가 쓰며, 이월할 때마다 이력을 남긴다.
 *
 * <p>12월에서 넘기면 다음 해 1월로 들어가며, 이때는 연 예산도 함께 맞춘다(넘기는 해의 이월 유출, 받는 해의 이월 유입). 연 예산이 그대로면 받은 금액을
 * 연 예산 한도 때문에 다 쓰지 못하기 때문이다. 같은 해 안의 이월은 연 예산에 영향이 없다.
 */
@Service
@RequiredArgsConstructor
public class CashbackBudgetTransferService {

  private final CashbackAnnualBudgetRepository annualBudgetRepository;
  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackBudgetTransferRepository transferRepository;
  private final Clock clock;

  /**
   * 해당 월의 남은 예산에서 금액을 다음 달로 넘긴다. 부분 이월도 된다.
   *
   * <p>남은 예산 확인과 차감은 조건부 UPDATE 한 문장이라, 동시에 들어온 예산 확보·복원과 섞여도 남은 예산이 음수가 되지 않는다. 잠그는 순서는 연 예산,
   * 월 예산이고 넘기는 쪽을 먼저, 받는 쪽을 나중에 한다. 적립 중단·마감 상태와 상관없이 넘길 수 있다.
   *
   * @throws BusinessException 월·금액이 올바르지 않을 때(CSH-007), 넘기는 달이나 받는 달의 월 예산(연말이면 연 예산도)이 없을 때(CSH-004), 남은 예산보다
   *     많이 넘기려 할 때(CSH-011). 예외가 나면 이미 반영한 부분도 함께 롤백된다
   */
  @Transactional
  public BudgetTransferResponse transfer(BudgetTransferRequest request) {
    int year = request.year();
    int month = request.month();
    long amount = request.amount();
    CashbackMonthlyBudget.validateMonth(month);
    if (amount <= 0) {
      throw new BusinessException(CashbackErrorCode.INVALID_BUDGET, "금액은 0보다 커야 합니다");
    }
    YearMonth next = YearMonth.of(year, month).plusMonths(1);
    boolean crossYear = next.getYear() != year;

    CashbackMonthlyBudget from = requireMonthly(year, month, "이월을 내보내는 달");
    CashbackMonthlyBudget to = requireMonthly(next.getYear(), next.getMonthValue(), "이월을 받는 달");
    if (crossYear && !annualBudgetRepository.existsByBudgetYear(next.getYear())) {
      throw new BusinessException(CashbackErrorCode.BUDGET_NOT_FOUND, "이월을 받는 해의 연 예산이 없습니다");
    }

    // 넘기는 쪽 먼저(연 -> 월), 받는 쪽 나중(연 -> 월)
    if (crossYear && annualBudgetRepository.transferOut(year, amount) == 0) {
      throw new BusinessException(CashbackErrorCode.TRANSFER_EXCEEDS_REMAINING);
    }
    if (monthlyBudgetRepository.transferOut(year, month, amount) == 0) {
      throw new BusinessException(CashbackErrorCode.TRANSFER_EXCEEDS_REMAINING);
    }
    if (crossYear) {
      annualBudgetRepository.transferIn(next.getYear(), amount);
    }
    monthlyBudgetRepository.transferIn(next.getYear(), next.getMonthValue(), amount);

    CashbackBudgetTransfer transfer =
        transferRepository.save(
            CashbackBudgetTransfer.manual(
                from.getId(), to.getId(), amount, LocalDateTime.now(clock)));
    return BudgetTransferResponse.of(transfer, from, to);
  }

  /** 해당 연도의 월에서 넘긴 이월 이력을 최근 이월순으로 돌려준다. */
  @Transactional(readOnly = true)
  public List<BudgetTransferResponse> list(int year) {
    List<CashbackBudgetTransfer> transfers = transferRepository.findAllFromYear(year);
    Map<Long, CashbackMonthlyBudget> budgets =
        monthlyBudgetRepository
            .findAllById(
                transfers.stream()
                    .flatMap(t -> java.util.stream.Stream.of(t.getFromMonthlyBudgetId(), t.getToMonthlyBudgetId()))
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(CashbackMonthlyBudget::getId, Function.identity()));
    return transfers.stream()
        .map(
            t ->
                BudgetTransferResponse.of(
                    t, budgets.get(t.getFromMonthlyBudgetId()), budgets.get(t.getToMonthlyBudgetId())))
        .toList();
  }

  private CashbackMonthlyBudget requireMonthly(int year, int month, String role) {
    return monthlyBudgetRepository
        .findByBudgetYearAndBudgetMonth(year, month)
        .orElseThrow(
            () ->
                new BusinessException(
                    CashbackErrorCode.BUDGET_NOT_FOUND, role + "의 월 예산이 없습니다"));
  }
}
