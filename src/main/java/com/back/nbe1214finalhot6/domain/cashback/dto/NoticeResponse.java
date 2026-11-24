package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackNotice;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이용자에게 보이는 캐시백 공지 한 건.
 *
 * @param noticeId 공지 ID
 * @param type 공지 종류
 * @param content 공지 문구. 날짜만 쓰고 시각은 쓰지 않는다
 * @param effectiveFrom 공지가 알리는 내용이 적용되는 첫 날. 예고인지 이미 적용됐는지는 오늘 날짜와 비교해서 판단한다
 * @param stopAt 중단 예정일. 중단과 관련 없는 공지는 null
 * @param createdAt 공지 등록 시각
 * @param rate 정책 변경 예고의 새 요율(소수 비율, 3% = 0.03). 다른 종류의 공지는 null
 * @param previousRate 새 요율이 적용되기 직전의 요율(소수 비율). 정책 변경 예고가 아니거나 직전 요율을 알 수 없으면 null
 */
public record NoticeResponse(
    Long noticeId,
    NoticeType type,
    String content,
    LocalDate effectiveFrom,
    LocalDate stopAt,
    LocalDateTime createdAt,
    BigDecimal rate,
    BigDecimal previousRate) {

  /** 요율 정보가 없는 공지(소진 예고, 긴급 중단 등). */
  public static NoticeResponse from(CashbackNotice notice) {
    return of(notice, null, null);
  }

  public static NoticeResponse of(CashbackNotice notice, BigDecimal rate, BigDecimal previousRate) {
    return new NoticeResponse(
        notice.getId(),
        notice.getType(),
        notice.getContent(),
        notice.getEffectiveFrom(),
        notice.getStopAt(),
        notice.getCreatedAt(),
        rate,
        previousRate);
  }
}
