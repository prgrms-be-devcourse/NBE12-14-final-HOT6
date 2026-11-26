package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunDetailResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutRunSummaryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PayoutStatusResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPayoutRun;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPayoutRunItem;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutItemResult;
import com.back.nbe1214finalhot6.domain.cashback.entity.PayoutTrigger;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPayoutRunItemRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPayoutRunRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.PayableTotal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지급 배치의 실행 기록과 현황. 지급 처리 자체는 {@link CashbackPayoutService} 가 한다. */
@Service
@RequiredArgsConstructor
public class CashbackPayoutStatusService {

  private static final int MAX_PAGE_SIZE = 100;

  // 상세에서는 확인이 필요한 건이 먼저 보인다
  private static final List<PayoutItemResult> DETAIL_ORDER =
      List.of(PayoutItemResult.FAILED, PayoutItemResult.SKIPPED, PayoutItemResult.PAID);

  private final CashbackEarningRepository earningRepository;
  private final CashbackPayoutRunRepository runRepository;
  private final CashbackPayoutRunItemRepository itemRepository;
  private final Clock clock;

  /** 실행 결과와 처리한 건을 남긴다. 자동 실행은 지급이나 실패가 없으면 남기지 않아, 빈 실행이 기록을 밀어내지 않는다. */
  @Transactional
  void record(
      LocalDateTime ranAt,
      PayoutTrigger trigger,
      PayoutRunResponse result,
      List<PayoutItemLog> items) {
    if (trigger == PayoutTrigger.SCHEDULED && result.paid() + result.failed() == 0) {
      return;
    }
    CashbackPayoutRun run =
        runRepository.save(
            CashbackPayoutRun.of(
                ranAt, trigger, result.paid(), result.skipped(), result.failed()));
    Map<Long, CashbackEarning> earnings =
        earningRepository.findAllById(items.stream().map(PayoutItemLog::earningId).toList()).stream()
            .collect(Collectors.toMap(CashbackEarning::getId, Function.identity()));
    itemRepository.saveAll(
        items.stream()
            .filter(item -> earnings.containsKey(item.earningId()))
            .map(
                item ->
                    CashbackPayoutRunItem.of(
                        run.getId(),
                        earnings.get(item.earningId()),
                        item.result(),
                        item.errorCode(),
                        item.errorMessage()))
            .toList());
  }

  /** 지금의 지급 대기. 읽기만 한다. */
  @Transactional(readOnly = true)
  public PayoutStatusResponse status() {
    LocalDateTime now = LocalDateTime.now(clock);
    PayableTotal pending = earningRepository.totalByStatus(EarningStatus.PENDING);
    PayableTotal due = earningRepository.totalPayable(now, EarningStatus.PENDING);
    return new PayoutStatusResponse(
        pending.getCount(),
        pending.getAmount(),
        due.getCount(),
        due.getAmount(),
        earningRepository.findNextPayableAt(now, EarningStatus.PENDING));
  }

  /**
   * 실행 목록. 시작 시각이 늦은 순이며, 같으면 나중에 남긴 것이 먼저다.
   *
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   */
  @Transactional(readOnly = true)
  public PageResponse<PayoutRunSummaryResponse> runs(int page, int size) {
    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("ranAt"), Sort.Order.desc("id")));
    return PageResponse.from(runRepository.findAll(pageable).map(PayoutRunSummaryResponse::from));
  }

  /**
   * 실행 한 번의 상세.
   *
   * @throws BusinessException 없는 실행일 때(CSH-013)
   */
  @Transactional(readOnly = true)
  public PayoutRunDetailResponse detail(Long runId) {
    CashbackPayoutRun run =
        runRepository
            .findById(runId)
            .orElseThrow(() -> new BusinessException(CashbackErrorCode.PAYOUT_RUN_NOT_FOUND));
    List<PayoutRunDetailResponse.Item> items =
        itemRepository.findByRunIdOrderByEarningIdAsc(runId).stream()
            // 정렬은 안정적이라 같은 결과 안의 적립 건 순서는 유지된다
            .sorted(Comparator.comparingInt(item -> DETAIL_ORDER.indexOf(item.getResult())))
            .map(PayoutRunDetailResponse.Item::from)
            .toList();
    long paidAmount =
        items.stream()
            .filter(item -> item.result() == PayoutItemResult.PAID)
            .mapToLong(PayoutRunDetailResponse.Item::amount)
            .sum();
    return new PayoutRunDetailResponse(PayoutRunSummaryResponse.from(run), paidAmount, items);
  }
}
