package com.back.nbe1214finalhot6.domain.payment.dto;

import com.back.nbe1214finalhot6.common.response.PageResponse;
import java.time.LocalDate;

/**
 * 사장님 결제 내역. 조회 기간의 요약과 결제 목록을 함께 돌려준다.
 *
 * <p>집계 기준은 일 정산과 같다. 결제는 완료한 날, 취소는 취소한 날에 센다(취소돼도 결제는 완료한 날 그대로).
 *
 * @param from 조회 시작일(포함)
 * @param to 조회 끝일(포함)
 * @param paymentCount 기간 안에 완료한 결제 건수 (나중에 취소된 건도 포함)
 * @param paymentAmount 위 결제의 총액
 * @param cancelCount 기간 안에 취소한 건수 (결제는 기간 밖일 수 있다)
 * @param cancelAmount 위 취소의 총액
 * @param netSales 순매출 = 결제 총액 - 취소 총액 (취소가 더 많은 날은 음수)
 * @param payments 기간 안에 완료했거나 취소한 결제 목록. 최신순
 */
public record MerchantPaymentsResponse(
    LocalDate from,
    LocalDate to,
    long paymentCount,
    long paymentAmount,
    long cancelCount,
    long cancelAmount,
    long netSales,
    PageResponse<MerchantPaymentItem> payments) {}
