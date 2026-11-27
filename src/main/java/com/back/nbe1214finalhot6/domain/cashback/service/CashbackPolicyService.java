package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.config.CashbackProperties;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyHistoryResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReductionRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.RateReservationResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicyHistory;
import com.back.nbe1214finalhot6.domain.cashback.entity.PolicyBasis;
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
  private final CashbackPolicyBasisCollector basisCollector;
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
   * @param operatorId 인하한 운영자의 회원 ID. 이력의 변경자로 남는다
   * @return 새로 만든 구간
   * @throws BusinessException 요율·시각이 올바르지 않거나 현재 요율보다 낮지 않을 때(CSH-003), 월 예산이 없을 때(CSH-004),
   *     구간이 겹칠 때(CSH-002), 이번 달 인하 횟수를 넘을 때(CSH-009)
   */
  @Transactional
  public PolicyResponse reduceRate(RateReductionRequest request, Long operatorId) {
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

    // 구간을 바꾸기 전의 상황을 근거로 남긴다
    PolicyBasis basis = basisCollector.collect(currentRate);

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
      historyRepository.save(CashbackPolicyHistory.updated(before, covering, operatorId, now));
    }
    historyRepository.save(
        CashbackPolicyHistory.reduced(successor, operatorId, now, request.reason(), basis));
    // 이용자에게 보이는 정책 변경 예고. 같은 트랜잭션이라 인하가 롤백되면 공지도 롤백된다
    noticeService.registerPolicyChange(successor, request.notice(), now);
    return PolicyResponse.from(successor);
  }

  /**
   * 아직 적용되지 않은 인하 예약의 요율, 적용 시각, 사유, 공지를 수정한다. 월 인하 횟수는 늘지 않는다.
   *
   * <p>적용 시각을 옮기면 이 구간에 맞닿아 끝나던 앞 구간의 종료도 함께 옮긴다. 앞서 남긴 이력은 바꾸지 않고 새 이력을 남긴다.
   *
   * @throws BusinessException 없는 구간(CSH-001), 이미 적용이 시작된 구간(CSH-017), 요율·시각이 올바르지 않을 때(CSH-003),
   *     다른 구간과 겹칠 때(CSH-002), 공지가 올바르지 않을 때(CSH-014)
   */
  @Transactional
  public PolicyResponse reviseReduction(Long policyId, RateReductionRequest request, Long operatorId) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime oldStart =
        policyRepository
            .findStartsAt(policyId)
            .orElseThrow(() -> new BusinessException(CashbackErrorCode.POLICY_NOT_FOUND));
    YearMonth month = YearMonth.from(oldStart);
    // 인하와 같은 행을 잠가 같은 달의 인하·수정을 하나씩 처리한다
    CashbackMonthlyBudget budget =
        monthlyBudgetRepository
            .findWithLockByBudgetYearAndBudgetMonth(month.getYear(), month.getMonthValue())
            .orElseThrow(() -> new BusinessException(CashbackErrorCode.BUDGET_NOT_FOUND));
    CashbackPolicy target = pending(policyId, now);

    LocalDateTime newStart = request.effectiveAt();
    requireNewStart(newStart, target.getStartsAt(), now, month);
    CashbackPolicy.validateRate(request.rate());

    List<CashbackPolicy> others =
        policyRepository.findAllByOrderByStartsAtDesc().stream()
            .filter(p -> !p.getId().equals(policyId))
            .toList();
    // 이 구간이 시작되며 끝나던 앞 구간. 월초에 시작하는 예약은 지난달 구간을 건드리지 않는다
    CashbackPolicy preceding =
        target.getStartsAt().equals(month.atDay(1).atStartOfDay())
            ? null
            : others.stream()
                .filter(p -> target.getStartsAt().equals(p.getEndsAt()))
                .findFirst()
                .orElse(null);
    CashbackPolicy following =
        others.stream()
            .filter(p -> p.getStartsAt().equals(target.getEndsAt()))
            .findFirst()
            .orElse(null);
    if (others.stream()
        .anyMatch(p -> p != preceding && overlaps(p, newStart, target.getEndsAt()))) {
      throw new BusinessException(CashbackErrorCode.POLICY_PERIOD_OVERLAP);
    }
    BigDecimal previousRate = preceding != null ? preceding.getRate() : budget.getBaseRate();
    if (previousRate == null || request.rate().compareTo(previousRate) >= 0) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "새 요율은 그 시각의 이전 요율보다 낮아야 합니다");
    }
    if (following != null && request.rate().compareTo(following.getRate()) <= 0) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "새 요율은 다음에 예약한 요율보다 높아야 합니다");
    }

    PolicyBasis basis = basisCollector.collect(previousRate);
    if (preceding != null && !newStart.equals(preceding.getEndsAt())) {
      CashbackPolicy before = preceding.snapshot();
      preceding.moveEnd(newStart);
      historyRepository.save(CashbackPolicyHistory.updated(before, preceding, operatorId, now));
    }
    CashbackPolicy before = target.snapshot();
    target.reschedule(request.rate(), newStart);
    historyRepository.save(
        CashbackPolicyHistory.revised(before, target, operatorId, now, request.reason(), basis));
    noticeService.revisePolicyChange(target, request.notice(), now);
    return PolicyResponse.from(target);
  }

  /**
   * 아직 적용되지 않은 인하 예약의 지금 값.
   *
   * @throws BusinessException 없는 구간(CSH-001), 이미 적용이 시작된 구간(CSH-017)
   */
  @Transactional(readOnly = true)
  public RateReservationResponse getReservation(Long policyId) {
    CashbackPolicy policy = pending(policyId, LocalDateTime.now(clock));
    return new RateReservationResponse(
        policy.getId(),
        policy.getRate(),
        policy.getStartsAt(),
        historyRepository
            .findFirstByPolicyIdAndReasonIsNotNullOrderByIdDesc(policyId)
            .map(CashbackPolicyHistory::getReason)
            .orElse(null),
        noticeService.findPolicyNotice(policyId));
  }

  private CashbackPolicy pending(Long policyId, LocalDateTime now) {
    CashbackPolicy policy =
        policyRepository
            .findById(policyId)
            .orElseThrow(() -> new BusinessException(CashbackErrorCode.POLICY_NOT_FOUND));
    if (!policy.getStartsAt().isAfter(now)) {
      throw new BusinessException(CashbackErrorCode.POLICY_ALREADY_APPLIED);
    }
    return policy;
  }

  // 시각을 바꿀 때만 예고 일수를 다시 확인한다. 사유나 공지만 고치는 것은 언제든 할 수 있다
  private void requireNewStart(
      LocalDateTime newStart, LocalDateTime oldStart, LocalDateTime now, YearMonth month) {
    if (newStart == null || !YearMonth.from(newStart).equals(month)) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "적용 시각은 같은 달 안에서만 바꿀 수 있습니다");
    }
    if (newStart.equals(oldStart)) {
      return;
    }
    LocalDateTime earliest =
        now.truncatedTo(ChronoUnit.MINUTES).plusDays(properties.rateChangeNoticeDays());
    if (newStart.isBefore(earliest) || !newStart.isAfter(now)) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_POLICY, "적용 시각은 " + earliest + " 이후여야 합니다");
    }
  }

  private static boolean overlaps(CashbackPolicy p, LocalDateTime start, LocalDateTime end) {
    return (end == null || p.getStartsAt().isBefore(end))
        && (p.getEndsAt() == null || p.getEndsAt().isAfter(start));
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
