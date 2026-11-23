package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.EarningResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyLimitResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyLimitRepository;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingService;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용자 화면(내 캐시백, 적립 내역)이 쓰는 캐시백 조회. 읽기만 하고 한도·예산·적립 건을 바꾸지 않는다. 결제 도메인이 쓰는 {@link
 * CashbackService} 와 분리했다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CashbackQueryService {

  static final int MAX_PAGE_SIZE = 100;

  private final CashbackEarningRepository earningRepository;
  private final CashbackMonthlyLimitRepository limitRepository;
  private final OperatorSettingService settingService;
  private final Clock clock;

  /** 이번 달(서버 시각, 한국 시간) 한도 현황. 사용 기록이 없으면 사용량 0 이다. */
  public MonthlyLimitResponse getMonthlyLimit(Long walletId) {
    YearMonth month = YearMonth.now(clock);
    long limit = settingService.getLong(SettingKey.CASHBACK_USER_MONTHLY_LIMIT);
    long used = limitRepository.findUsedAmount(walletId, month.atDay(1)).orElse(0L);
    // 한도를 사용량보다 낮춘 경우처럼 음수가 되면 0 으로 돌려준다
    return new MonthlyLimitResponse(
        month.getYear(), month.getMonthValue(), limit, used, Math.max(0L, limit - used));
  }

  /**
   * 내 적립 내역을 최근 접수순으로 돌려준다. 상태를 주면 그 상태만 보여 준다.
   *
   * @param status 상태 필터. null 이면 전체
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   */
  public PageResponse<EarningResponse> listEarnings(
      Long walletId, EarningStatus status, int page, int size) {
    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id")));
    Page<CashbackEarning> result =
        status == null
            ? earningRepository.findByWalletId(walletId, pageable)
            : earningRepository.findByWalletIdAndStatus(walletId, status, pageable);
    return PageResponse.from(result.map(EarningResponse::from));
  }
}
