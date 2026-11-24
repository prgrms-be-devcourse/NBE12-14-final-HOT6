package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackNotice;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용자에게 보이는 캐시백 공지. 공지를 등록하는 쪽(요율 인하 등)은 캐시백 도메인 안의 서비스이고, 조회는 이용자 앱이 쓴다.
 *
 * <p>등록은 호출한 쪽의 트랜잭션에 참여한다. 요율 인하가 롤백되면 공지도 함께 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class CashbackNoticeService {

  static final int MAX_PAGE_SIZE = 100;

  private final CashbackNoticeRepository noticeRepository;
  private final CashbackPolicyRepository policyRepository;
  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;

  /**
   * 요율 인하로 새 구간이 만들어졌을 때 정책 변경 예고를 한 건 등록한다. 같은 정책에는 한 번만 만들어진다(유니크 제약).
   *
   * @param policy 새로 만든 요율 구간. 저장되어 id 가 있어야 한다
   */
  @Transactional
  public void registerPolicyChange(CashbackPolicy policy, LocalDateTime now) {
    noticeRepository.save(CashbackNotice.policyChange(policy, now));
  }

  /**
   * 월 예산이 공지({@code NOTICED}) 상태로 바뀌었을 때 소진 예고를 한 건 등록한다. 상태 전이가 한 번만 일어나므로 같은 달에 한 번만 불린다. 단순 INSERT
   * 한 번이라 예산 확보 경로의 잠금을 늘리지 않는다.
   *
   * @param emergencyLineRate 긴급 중단선(전체 예산 대비, 소수 비율)
   * @param noticeDays 공지 후 중단까지의 최대 일수
   * @param stopAt 중단 예정일
   */
  @Transactional
  public void registerBudgetNotice(
      BigDecimal emergencyLineRate, int noticeDays, LocalDate stopAt, LocalDateTime now) {
    noticeRepository.save(CashbackNotice.budgetNotice(emergencyLineRate, noticeDays, stopAt, now));
  }

  /** 월 예산이 긴급선에 닿거나 운영자가 긴급 중단해서 즉시 중단됐을 때 긴급 중단 공지를 한 건 등록한다. */
  @Transactional
  public void registerEmergencyStop(LocalDateTime now) {
    noticeRepository.save(CashbackNotice.emergencyStop(now));
  }

  /**
   * 공지 목록을 최근 등록순으로 돌려준다. 읽기만 한다.
   *
   * @param type 종류 필터. null 이면 전체
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   */
  @Transactional(readOnly = true)
  public PageResponse<NoticeResponse> list(NoticeType type, int page, int size) {
    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    Page<CashbackNotice> result =
        type == null
            ? noticeRepository.findAll(pageable)
            : noticeRepository.findByType(type, pageable);
    return PageResponse.from(toResponses(result));
  }

  /**
   * 정책 변경 예고에는 새 요율과 직전 요율을 채운다. 새 요율은 공지에 연결된 정책의 요율이고, 직전 요율은 그 정책이 시작하는 시각에 끝난 같은 달의 구간의
   * 요율이다. 같은 달에 앞선 구간이 없으면 그 달 월 예산의 기본 요율이다. 정책을 한 번에 읽어 공지마다 조회하지 않는다.
   */
  private Page<NoticeResponse> toResponses(Page<CashbackNotice> page) {
    Set<Long> policyIds =
        page.getContent().stream()
            .map(CashbackNotice::getPolicyId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (policyIds.isEmpty()) {
      return page.map(NoticeResponse::from);
    }
    Map<Long, CashbackPolicy> policies =
        policyRepository.findAllById(policyIds).stream()
            .collect(Collectors.toMap(CashbackPolicy::getId, Function.identity()));
    Set<LocalDateTime> startTimes =
        policies.values().stream().map(CashbackPolicy::getStartsAt).collect(Collectors.toSet());
    Map<LocalDateTime, CashbackPolicy> endedAt =
        policyRepository.findByEndsAtIn(startTimes).stream()
            .collect(Collectors.toMap(CashbackPolicy::getEndsAt, Function.identity(), (a, b) -> a));
    Map<YearMonth, BigDecimal> baseRates = new HashMap<>();
    return page.map(
        notice -> {
          CashbackPolicy policy =
              notice.getPolicyId() == null ? null : policies.get(notice.getPolicyId());
          if (policy == null) {
            return NoticeResponse.from(notice);
          }
          return NoticeResponse.of(
              notice, policy.getRate(), previousRate(policy, endedAt, baseRates));
        });
  }

  private BigDecimal previousRate(
      CashbackPolicy policy,
      Map<LocalDateTime, CashbackPolicy> endedAt,
      Map<YearMonth, BigDecimal> baseRates) {
    YearMonth month = YearMonth.from(policy.getStartsAt());
    CashbackPolicy previous = endedAt.get(policy.getStartsAt());
    if (previous != null && YearMonth.from(previous.getStartsAt()).equals(month)) {
      return previous.getRate();
    }
    return baseRates.computeIfAbsent(
        month,
        m ->
            monthlyBudgetRepository
                .findByBudgetYearAndBudgetMonth(m.getYear(), m.getMonthValue())
                .map(CashbackMonthlyBudget::getBaseRate)
                .orElse(null));
  }
}
