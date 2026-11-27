package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeAdminResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.NoticeUpdateRequest;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackMonthlyBudget;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackNotice;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeStatus;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import com.back.nbe1214finalhot6.setting.dto.SettingNoticeRequest;
import java.math.BigDecimal;
import java.time.Clock;
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
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용자에게 보이는 공지. 요율 인하, 적립 중단, 한도 변경이 등록하고 이용자 앱이 읽는다.
 *
 * <p>등록은 호출한 쪽의 트랜잭션에 참여한다. 요율 인하나 한도 변경이 롤백되면 공지도 함께 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class CashbackNoticeService {

  static final int MAX_PAGE_SIZE = 100;

  private final CashbackNoticeRepository noticeRepository;
  private final CashbackPolicyRepository policyRepository;
  private final CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  private final Clock clock;

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
   * 한도를 바꾸거나 예약할 때 알리는 공지를 등록한다.
   *
   * @return 등록한 공지 ID
   * @throws BusinessException 제목·본문이 비었거나 길 때, 홈에 띄우는데 종료일이 없거나 오늘·적용일보다 이를 때(CSH-014)
   */
  @Transactional
  public Long registerLimitChange(
      String settingKey,
      long previousValue,
      long newValue,
      LocalDate effectiveFrom,
      SettingNoticeRequest notice) {
    requireText(notice.title(), notice.content());
    requireHome(notice.showOnHome(), notice.homeUntil(), effectiveFrom);
    return noticeRepository
        .save(
            CashbackNotice.limitChange(
                settingKey,
                previousValue,
                newValue,
                effectiveFrom,
                notice.title(),
                notice.content(),
                notice.showOnHome(),
                notice.homeUntil(),
                LocalDateTime.now(clock)))
        .getId();
  }

  /** 알리던 예약이 취소된 공지를 거둔다. 없는 ID 면 아무것도 하지 않는다. */
  @Transactional
  public void withdraw(Long noticeId) {
    noticeRepository.findById(noticeId).ifPresent(CashbackNotice::withdraw);
  }

  /** 이용자 홈에 띄울 공지. 거두지 않았고 홈 노출이며 종료일이 지나지 않은 것 중 최근 5건이다. */
  @Transactional(readOnly = true)
  public List<NoticeResponse> listForHome() {
    List<CashbackNotice> notices =
        noticeRepository
            .findTop5ByStatusAndShowOnHomeTrueAndHomeUntilGreaterThanEqualOrderByCreatedAtDescIdDesc(
                NoticeStatus.PUBLISHED, LocalDate.now(clock));
    return toResponses(new PageImpl<>(notices)).getContent();
  }

  /**
   * 운영자가 보는 공지 목록. 거둔 공지도 포함해 최근 등록순으로 돌려준다. 읽기만 한다.
   *
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   */
  @Transactional(readOnly = true)
  public PageResponse<NoticeAdminResponse> listForAdmin(int page, int size) {
    Page<CashbackNotice> result = noticeRepository.findAll(pageOf(page, size));
    List<NoticeResponse> notices = toResponses(result).getContent();
    List<NoticeAdminResponse> content =
        IntStream.range(0, notices.size())
            .mapToObj(i -> NoticeAdminResponse.of(result.getContent().get(i), notices.get(i)))
            .toList();
    return PageResponse.from(
        new PageImpl<>(content, result.getPageable(), result.getTotalElements()));
  }

  /**
   * 게시 중인 공지의 문구와 홈 노출을 고친다. 알리는 값과 적용일은 바뀌지 않는다.
   *
   * @throws BusinessException 없는 공지일 때(CSH-015), 거둔 공지이거나 내용·홈 종료일이 올바르지 않을 때(CSH-014)
   */
  @Transactional
  public NoticeAdminResponse update(Long noticeId, NoticeUpdateRequest request) {
    CashbackNotice notice =
        noticeRepository
            .findById(noticeId)
            .orElseThrow(() -> new BusinessException(CashbackErrorCode.NOTICE_NOT_FOUND));
    if (notice.getStatus() == NoticeStatus.WITHDRAWN) {
      throw new BusinessException(CashbackErrorCode.INVALID_NOTICE, "거둔 공지는 고칠 수 없습니다");
    }
    requireText(request.title(), request.content());
    // 이미 지난 종료일을 그대로 둔 채 문구만 고치는 것은 허용한다
    boolean sameHome =
        notice.isShowOnHome() && Objects.equals(notice.getHomeUntil(), request.homeUntil());
    if (!sameHome) {
      requireHome(request.showOnHome(), request.homeUntil(), notice.getEffectiveFrom());
    }
    notice.edit(
        request.title(),
        request.content(),
        request.showOnHome(),
        request.homeUntil(),
        LocalDateTime.now(clock));
    return NoticeAdminResponse.of(
        notice, toResponses(new PageImpl<>(List.of(notice))).getContent().get(0));
  }

  private static void requireText(String title, String content) {
    if (title == null
        || title.isBlank()
        || title.length() > CashbackNotice.TITLE_LENGTH
        || content == null
        || content.isBlank()
        || content.length() > CashbackNotice.CONTENT_LENGTH) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_NOTICE, "제목은 100자, 본문은 500자까지 입력해야 합니다");
    }
  }

  // 적용되기 전에 홈에서 내려가지 않도록 종료일은 오늘과 적용일 이후여야 한다
  private void requireHome(boolean showOnHome, LocalDate homeUntil, LocalDate effectiveFrom) {
    if (!showOnHome) {
      return;
    }
    LocalDate today = LocalDate.now(clock);
    LocalDate earliest =
        effectiveFrom != null && effectiveFrom.isAfter(today) ? effectiveFrom : today;
    if (homeUntil == null || homeUntil.isBefore(earliest)) {
      throw new BusinessException(
          CashbackErrorCode.INVALID_NOTICE, "홈에 띄우려면 오늘과 적용일 이후의 종료일이 필요합니다");
    }
  }

  private static Pageable pageOf(int page, int size) {
    return PageRequest.of(
        page,
        Math.min(size, MAX_PAGE_SIZE),
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
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
   * 게시 중인 공지 목록을 최근 등록순으로 돌려준다. 읽기만 한다.
   *
   * @param type 종류 필터. null 이면 전체
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   */
  @Transactional(readOnly = true)
  public PageResponse<NoticeResponse> list(NoticeType type, int page, int size) {
    Pageable pageable = pageOf(page, size);
    Page<CashbackNotice> result =
        type == null
            ? noticeRepository.findByStatus(NoticeStatus.PUBLISHED, pageable)
            : noticeRepository.findByTypeAndStatus(type, NoticeStatus.PUBLISHED, pageable);
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
