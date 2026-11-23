package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyHistoryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicyHistory;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyHistoryRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐시백 정책(요율 구간) 관리.
 *
 * <p>월 기본 요율은 월 예산이 갖고, 구간은 달 중 요율을 낮출 때만 만든다. 구간을 직접 등록하는 API 는 없다.
 */
@Service
@RequiredArgsConstructor
public class CashbackPolicyService {

  static final int MAX_HISTORY_PAGE_SIZE = 100;

  private final CashbackPolicyRepository policyRepository;
  private final CashbackPolicyHistoryRepository historyRepository;
  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final CashbackNoticeService noticeService;
  private final CashbackProperties properties;
  private final Clock clock;

  /**
   * 달 중 요율을 낮춘다. 적용 시각이 속한 달 안에서 현재 구간을 적용 시각에 끝내고, 월 말까지 새 요율의 구간을 만든다.
   *
   * <p>그 달의 월 예산 행을 잠가서 같은 달의 인하를 한 번에 하나씩 처리한다. 횟수 검사와 구간 생성 사이에 다른 인하가 끼어들 수 없다. 연 예산은
   * 잠그지 않으므로 잠금 순서(연 -> 월)와 어긋나지 않는다. 이미 접수된 적립은 저장된 요율을 유지하므로 영향이 없다.
   *
   * <p>인하하면 이용자에게 보이는 정책 변경 예고 공지를 한 건 등록한다.
   *
   * @return 새로 만든 구간
   * @throws BusinessException 요율·시각이 올바르지 않거나 현재 요율보다 낮지 않을 때(CSH-003), 월 예산이 없을 때(CSH-004),
   *     구간이 겹칠 때(CSH-002), 이번 달 인하 횟수를 넘을 때(CSH-009)
   */
  @Transactional
  public PolicyResponse reduceRate(RateReductionRequest request) {
    LocalDateTime now = LocalDateTime.now(clock);
    CashbackPolicy.validateRate(request.rate());
    LocalDateTime effectiveAt = request.effectiveAt();
    LocalDateTime earliest =
        now.truncatedTo(ChronoUnit.MINUTES).plusDays(properties.rateChangeNoticeDays());
    if (effectiveAt == null || effectiveAt.isBefore(earliest)) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "적용 시각은 " + earliest + " 이후여야 합니다");
    }

    YearMonth month = YearMonth.from(effectiveAt);
    CashbackMonthlyBudget budget =
        monthlyBudgetRepository
            .findWithLockByBudgetYearAndBudgetMonth(month.getYear(), month.getMonthValue())
            .orElseThrow(
                () ->
                    new BusinessException(
                        CashbackErrorCode.BUDGET_NOT_FOUND, "적용 시각이 속한 달의 월 예산이 없습니다"));

    LocalDateTime monthStart = month.atDay(1).atStartOfDay();
    LocalDateTime nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay();
    long reductions =
        policyRepository.countByStartsAtGreaterThanEqualAndStartsAtLessThan(
            monthStart, nextMonthStart);
    if (reductions >= properties.maxRateReductionsPerMonth()) {
      throw new BusinessException(CashbackErrorCode.RATE_REDUCTION_LIMIT_EXCEEDED);
    }

    CashbackPolicy covering =
        policyRepository.findEffective(effectiveAt).stream().findFirst().orElse(null);
    BigDecimal currentRate = covering != null ? covering.getRate() : budget.getBaseRate();
    if (currentRate == null) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "그 달의 기본 요율이 없어 인하할 수 없습니다");
    }
    if (request.rate().compareTo(currentRate) >= 0) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY,
          "새 요율은 현재 요율(" + currentRate.toPlainString() + ")보다 낮아야 합니다");
    }

    CashbackPolicy before = null;
    if (covering != null) {
      before = covering.snapshot();
      covering.shortenEnd(effectiveAt);
    }
    CashbackPolicy successor =
        CashbackPolicy.create(request.rate(), effectiveAt, nextMonthStart, now);
    if (policyRepository.existsOverlapping(successor.getStartsAt(), successor.getEndsAt())) {
      throw new BusinessException(CashbackErrorCode.POLICY_PERIOD_OVERLAP);
    }
    policyRepository.save(successor);

    if (covering != null) {
      historyRepository.save(CashbackPolicyHistory.updated(before, covering, null, now));
    }
    historyRepository.save(CashbackPolicyHistory.created(successor, null, now));
    // 이용자에게 보이는 정책 변경 예고. 같은 트랜잭션이라 인하가 롤백되면 공지도 롤백된다
    noticeService.registerPolicyChange(successor, now);
    return PolicyResponse.from(successor);
  }

  /**
   * 정책 변경 이력을 최근 변경순으로 돌려준다. 읽기만 하고 이력을 바꾸지 않는다.
   *
   * @param policyId 한 구간의 이력만 볼 때의 정책 ID. null 이면 전체. 없는 ID 면 빈 목록이다
   * @param size 한 페이지 건수. {@value #MAX_HISTORY_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   */
  @Transactional(readOnly = true)
  public PageResponse<PolicyHistoryResponse> findHistory(Long policyId, int page, int size) {
    // 요율 인하 한 번이 "끝난 구간 UPDATE" 와 "새 구간 CREATE" 를 같은 시각에 남기므로 ID 로 순서를 확정한다
    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_HISTORY_PAGE_SIZE),
            Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("id")));
    Page<CashbackPolicyHistory> result =
        policyId == null
            ? historyRepository.findAll(pageable)
            : historyRepository.findAllByPolicyId(policyId, pageable);
    return PageResponse.from(result.map(PolicyHistoryResponse::from));
  }

  /** 시작이 늦은 순으로 돌려준다. */
  @Transactional(readOnly = true)
  public List<PolicyResponse> findAll() {
    return policyRepository.findAllByOrderByStartsAtDesc().stream()
        .map(PolicyResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public PolicyResponse get(Long policyId) {
    return policyRepository
        .findById(policyId)
        .map(PolicyResponse::from)
        .orElseThrow(() -> new BusinessException(CashbackErrorCode.POLICY_NOT_FOUND));
  }

  /** 해당 시각에 유효한 정책(요율 구간). 달 중 인하 구간이 있을 때만 있다. */
  @Transactional(readOnly = true)
  public Optional<PolicyResponse> findEffective(LocalDateTime at) {
    return policyRepository.findEffective(at).stream().findFirst().map(PolicyResponse::from);
  }

  /**
   * 해당 시각에 적용할 요율. 인하 구간이 있으면 그 요율, 없으면 그 달 월 예산의 기본 요율이다. 둘 다 없으면 비어 있다. 적립 계산이 접수 시각의 요율을 구할
   * 때 쓴다.
   */
  @Transactional(readOnly = true)
  public Optional<BigDecimal> effectiveRate(LocalDateTime at) {
    Optional<CashbackPolicy> segment = policyRepository.findEffective(at).stream().findFirst();
    if (segment.isPresent()) {
      return segment.map(CashbackPolicy::getRate);
    }
    return monthlyBudgetRepository
        .findByBudgetYearAndBudgetMonth(at.getYear(), at.getMonthValue())
        .map(CashbackMonthlyBudget::getBaseRate);
  }
}
