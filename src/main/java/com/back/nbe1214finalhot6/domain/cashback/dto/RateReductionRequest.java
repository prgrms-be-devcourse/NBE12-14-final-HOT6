package com.back.nbe1214finalhot6.domain.cashback.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 요율 인하 요청. 예약한 인하를 수정할 때도 같은 값을 모두 보낸다.
 *
 * @param rate 새 요율. 소수 비율(3% = 0.03)이며 적용 시각의 현재 요율보다 낮아야 한다
 * @param effectiveAt 적용 시각. 현재 분 + 예고 일수 이후이고, 이 시각이 속한 달 안에서만 적용한다
 * @param reason 인하 사유(200자까지). 운영자만 보는 기록이며 정책 변경 이력에 남는다
 * @param notice 이용자에게 알릴 공지. API 로는 필수다
 */
public record RateReductionRequest(
    @NotNull BigDecimal rate,
    @NotNull LocalDateTime effectiveAt,
    @NotBlank @Size(max = 200) String reason,
    @NotNull @Valid RateNoticeRequest notice) {

  /** 공지를 따로 쓰지 않는 호출. 서버가 만드는 기본 문구로 알린다. */
  public RateReductionRequest(BigDecimal rate, LocalDateTime effectiveAt, String reason) {
    this(rate, effectiveAt, reason, null);
  }
}
