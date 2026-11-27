package com.back.nbe1214finalhot6.domain.cashback.dto;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackNotice;
import com.back.nbe1214finalhot6.domain.cashback.entity.NoticeStatus;
import java.time.LocalDate;

/**
 * 운영자가 보는 공지 한 건. 거둔 공지도 포함한다.
 *
 * @param notice 이용자에게 보이는 내용
 * @param status 게시 중인지, 예약 취소로 거뒀는지
 * @param showOnHome 홈에 띄우는지
 * @param homeUntil 홈에 띄우는 마지막 날. 띄우지 않으면 null
 */
public record NoticeAdminResponse(
    NoticeResponse notice, NoticeStatus status, boolean showOnHome, LocalDate homeUntil) {

  public static NoticeAdminResponse of(CashbackNotice entity, NoticeResponse notice) {
    return new NoticeAdminResponse(
        notice, entity.getStatus(), entity.isShowOnHome(), entity.getHomeUntil());
  }
}
