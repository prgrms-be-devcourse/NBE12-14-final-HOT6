package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackNotice;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeType;
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
 */
public record NoticeResponse(
    Long noticeId,
    NoticeType type,
    String content,
    LocalDate effectiveFrom,
    LocalDate stopAt,
    LocalDateTime createdAt) {

  public static NoticeResponse from(CashbackNotice notice) {
    return new NoticeResponse(
        notice.getId(),
        notice.getType(),
        notice.getContent(),
        notice.getEffectiveFrom(),
        notice.getStopAt(),
        notice.getCreatedAt());
  }
}
