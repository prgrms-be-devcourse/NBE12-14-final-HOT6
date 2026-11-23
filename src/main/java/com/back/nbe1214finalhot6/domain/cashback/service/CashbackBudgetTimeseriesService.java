package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetMonthSeries;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTimePoint;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackBudgetTransfer;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetTransferRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.DailyAmount;
import com.back.nbe1214finalhot6.domain.cashback.repository.EarningExtent;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 대시보드의 예산 현황 그래프가 쓰는 일별 시계열. 새로 저장하지 않고 적립 건의 접수·지급·취소 시각과 이월 이력의 시각을 날짜로 묶어 계산한다.
 *
 * <p>월 예산마다 한 시리즈이고, 점의 값(지급·확보·남은 예산)은 그날 끝 시점의 누적 값이라 조회 기간을 바꿔도 같다. 지급과 취소는 접수 며칠 뒤에 일어나고 이월은
 * 월이 끝난 뒤에도 일어나므로, 시리즈는 월 말 이후 마지막 변동 날짜까지 이어진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashbackBudgetTimeseriesService {

  static final int MAX_DAYS = 366;

  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackEarningRepository earningRepository;
  private final CashbackBudgetTransferRepository transferRepository;
  private final Clock clock;

  /**
   * {@code from} 부터 {@code to} 까지(양 끝 포함)와 겹치는 월 예산의 일별 시계열을 오래된 달부터 돌려준다. 점은 오늘까지만 만들고, 겹치는 월 예산이 없으면 빈
   * 목록이다.
   *
   * @throws BusinessException 기간이 비었거나 {@value #MAX_DAYS}일을 넘을 때(CSH-007)
   */
  public List<BudgetMonthSeries> getTimeseries(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_BUDGET, "기간은 시작이 끝보다 늦지 않고 " + MAX_DAYS + "일 이내여야 합니다");
    }
    LocalDate today = LocalDate.now(clock);
    LocalDate end = to.isAfter(today) ? today : to;
    if (from.isAfter(end)) {
      return List.of();
    }

    Map<LocalDate, LocalDate> lastEarningDay = lastEarningDays();
    Map<Long, List<CashbackBudgetTransfer>> transfers = transfersByBudget();
    List<BudgetMonthSeries> result = new ArrayList<>();
    List<CashbackMonthlyBudget> budgets =
        monthlyBudgetRepository.findAll().stream()
            .sorted(
                Comparator.comparingInt(CashbackMonthlyBudget::getBudgetYear)
                    .thenComparingInt(CashbackMonthlyBudget::getBudgetMonth))
            .toList();
    for (CashbackMonthlyBudget budget : budgets) {
      LocalDate monthStart = YearMonth.of(budget.getBudgetYear(), budget.getBudgetMonth()).atDay(1);
      if (monthStart.isAfter(end)) {
        continue;
      }
      List<CashbackBudgetTransfer> budgetTransfers = transfers.getOrDefault(budget.getId(), List.of());
      LocalDate lastDay = lastDay(monthStart, lastEarningDay.get(monthStart), budgetTransfers);
      if (lastDay.isBefore(from)) {
        continue;
      }
      result.add(series(budget, monthStart, lastDay, from, end, budgetTransfers));
    }
    return result;
  }

  private BudgetMonthSeries series(
      CashbackMonthlyBudget budget,
      LocalDate monthStart,
      LocalDate lastDay,
      LocalDate from,
      LocalDate end,
      List<CashbackBudgetTransfer> transfers) {
    Map<LocalDate, Long> reserved = toMap(earningRepository.sumReservedByDay(monthStart));
    Map<LocalDate, Long> paid = toMap(earningRepository.sumPaidByDay(monthStart));
    Map<LocalDate, Long> canceled =
        toMap(earningRepository.sumCanceledByDay(monthStart, EarningStatus.CANCELED));
    Map<LocalDate, Long> carried = new HashMap<>();
    for (CashbackBudgetTransfer transfer : transfers) {
      long signed =
          transfer.getToMonthlyBudgetId().equals(budget.getId())
              ? transfer.getAmount()
              : -transfer.getAmount();
      carried.merge(transfer.getCreatedAt().toLocalDate(), signed, Long::sum);
    }

    LocalDate seriesEnd = lastDay.isBefore(end) ? lastDay : end;
    List<BudgetTimePoint> points = new ArrayList<>();
    long cumReserved = 0;
    long cumPaid = 0;
    long cumCanceled = 0;
    // 이전 달에서 월 시작 전에 넘어온 이월은 첫 점부터 반영한다
    long cumCarried = 0;
    for (Map.Entry<LocalDate, Long> entry : carried.entrySet()) {
      if (entry.getKey().isBefore(monthStart)) {
        cumCarried += entry.getValue();
      }
    }
    // 값은 월 시작부터 누적하고, 조회 기간 안의 날짜만 점으로 내보낸다
    for (LocalDate day = monthStart; !day.isAfter(seriesEnd); day = day.plusDays(1)) {
      cumReserved += reserved.getOrDefault(day, 0L);
      cumPaid += paid.getOrDefault(day, 0L);
      cumCanceled += canceled.getOrDefault(day, 0L);
      cumCarried += carried.getOrDefault(day, 0L);
      if (!day.isBefore(from)) {
        long outstanding = cumReserved - cumCanceled - cumPaid;
        points.add(
            new BudgetTimePoint(
                day,
                cumPaid,
                outstanding,
                budget.getAllocatedAmount() + cumCarried - outstanding - cumPaid));
      }
    }
    return new BudgetMonthSeries(
        budget.getBudgetYear(),
        budget.getBudgetMonth(),
        budget.getAllocatedAmount(),
        budget.getContingencyAmount(),
        points);
  }

  /** 시리즈가 이어지는 마지막 날짜. 월 말, 가장 늦은 지급·취소, 가장 늦은 이월 중 가장 늦은 날이다. */
  private static LocalDate lastDay(
      LocalDate monthStart, LocalDate lastEarningDay, List<CashbackBudgetTransfer> transfers) {
    LocalDate last = YearMonth.from(monthStart).atEndOfMonth();
    if (lastEarningDay != null && lastEarningDay.isAfter(last)) {
      last = lastEarningDay;
    }
    for (CashbackBudgetTransfer transfer : transfers) {
      LocalDate day = transfer.getCreatedAt().toLocalDate();
      if (day.isAfter(last)) {
        last = day;
      }
    }
    return last;
  }

  /** 월 예산의 첫날마다 그 달에 속한 적립의 가장 늦은 지급·취소 날짜. */
  private Map<LocalDate, LocalDate> lastEarningDays() {
    Map<LocalDate, LocalDate> result = new HashMap<>();
    for (EarningExtent extent : earningRepository.findExtents()) {
      LocalDate latest = null;
      if (extent.getLastPaidAt() != null) {
        latest = extent.getLastPaidAt().toLocalDate();
      }
      if (extent.getLastCanceledAt() != null) {
        LocalDate canceled = extent.getLastCanceledAt().toLocalDate();
        latest = latest == null || canceled.isAfter(latest) ? canceled : latest;
      }
      if (latest != null) {
        result.put(extent.getLimitMonth(), latest);
      }
    }
    return result;
  }

  /** 월 예산마다 그 달이 보내거나 받은 이월 이력. 이월 이력은 INSERT only 이고 건수가 적다. */
  private Map<Long, List<CashbackBudgetTransfer>> transfersByBudget() {
    Map<Long, List<CashbackBudgetTransfer>> result = new HashMap<>();
    for (CashbackBudgetTransfer transfer : transferRepository.findAll()) {
      result.computeIfAbsent(transfer.getFromMonthlyBudgetId(), id -> new ArrayList<>()).add(transfer);
      result.computeIfAbsent(transfer.getToMonthlyBudgetId(), id -> new ArrayList<>()).add(transfer);
    }
    return result;
  }

  private static Map<LocalDate, Long> toMap(List<DailyAmount> amounts) {
    Map<LocalDate, Long> map = new HashMap<>();
    for (DailyAmount amount : amounts) {
      map.merge(amount.getDay(), amount.getAmount(), Long::sum);
    }
    return map;
  }
}
