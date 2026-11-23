package com.back.nbe1214finalhot6.domain.cashback.dto;

import java.time.LocalDate;

/**
 * 월 예산의 하루치 점. 값은 그날 끝 시점의 누적 값이며 월 시작부터 계산한다(조회 기간과 무관).
 *
 * @param date 날짜(한국 시간)
 * @param paidAmount 지급 금액. 그날까지 지급된 적립 금액의 합이며 회수해도 줄지 않는다
 * @param reservedAmount 확보 금액. 접수된 적립(제외 건 제외)에서 지급된 금액과 지급 전 취소로 돌려받은 금액을 뺀, 아직 지급 전인 금액
 * @param remainingAmount 남은 예산. (배정 + 그날까지의 이월 유입 - 이월 유출) - 확보 - 지급
 */
public record BudgetTimePoint(
    LocalDate date, long paidAmount, long reservedAmount, long remainingAmount) {}
