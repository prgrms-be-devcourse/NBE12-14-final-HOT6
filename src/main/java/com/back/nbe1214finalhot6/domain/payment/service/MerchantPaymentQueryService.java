package com.back.nbe1214finalhot6.domain.payment.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.payment.dto.MerchantPaymentItem;
import com.back.nbe1214finalhot6.domain.payment.dto.MerchantPaymentsResponse;
import com.back.nbe1214finalhot6.domain.payment.dto.SalesTotals;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사장님 결제 내역(PAY-04). 가맹점의 결제·취소·순매출을 기간별로 읽기만 한다.
 *
 * <p>집계 기준은 일 정산과 같다: 결제는 {@code completedAt}의 날짜(상태가 완료 또는 취소), 취소는 {@code canceledAt}의 날짜이고 금액은 결제
 * 총액이다. 처리 중·실패 건은 완료 시각이 없어 빠진다. 어느 가맹점인지는 호출한 쪽(컨트롤러)이 토큰의 회원으로 정한다.
 */
@Service
@RequiredArgsConstructor
public class MerchantPaymentQueryService {

  static final int MAX_PERIOD_DAYS = 31;
  static final int MAX_PAGE_SIZE = 100;

  // 결제로 세는 상태: 취소돼도 결제는 완료한 날의 결제다
  private static final List<PaymentStatus> SALES_STATUSES =
      List.of(PaymentStatus.COMPLETED, PaymentStatus.CANCELED);

  private final PaymentRepository paymentRepository;
  private final Clock clock;

  /**
   * 기간 안의 결제 요약과 목록을 돌려준다.
   *
   * @param from 시작일(포함). 없으면 {@code to}, 둘 다 없으면 오늘
   * @param to 끝일(포함). 없으면 {@code from}
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   * @throws BusinessException 시작이 끝보다 늦거나 {@value #MAX_PERIOD_DAYS}일을 넘으면 PAY-008
   */
  @Transactional(readOnly = true)
  public MerchantPaymentsResponse get(
      Long merchantId, LocalDate from, LocalDate to, int page, int size) {
    LocalDate start = from != null ? from : (to != null ? to : LocalDate.now(clock));
    LocalDate end = to != null ? to : start;
    if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
      throw new BusinessException(PaymentErrorCode.PAYMENT_INVALID_PERIOD);
    }
    LocalDateTime startAt = start.atStartOfDay();
    LocalDateTime endAt = end.plusDays(1).atStartOfDay();

    SalesTotals payments =
        paymentRepository.totalsByCompletedAt(merchantId, startAt, endAt, SALES_STATUSES);
    SalesTotals cancels =
        paymentRepository.totalsByCanceledAt(merchantId, startAt, endAt, PaymentStatus.CANCELED);

    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("completedAt"), Sort.Order.desc("id")));
    PageResponse<MerchantPaymentItem> items =
        PageResponse.from(
            paymentRepository
                .findSalesBetween(
                    merchantId, startAt, endAt, SALES_STATUSES, PaymentStatus.CANCELED, pageable)
                .map(MerchantPaymentItem::from));

    return new MerchantPaymentsResponse(
        start,
        end,
        payments.count(),
        payments.amount(),
        cancels.count(),
        cancels.amount(),
        payments.amount() - cancels.amount(),
        items);
  }

  /**
   * 결제 번호로 내 가맹점의 결제 한 건을 돌려준다. 목록과 같이 완료·취소된 결제만 조회한다.
   *
   * <p>없는 번호, 다른 가맹점의 결제, 처리 중·실패한 결제는 모두 PAY-003 으로 똑같이 응답해서 다른 가맹점의 결제가 있는지 알려 주지 않는다.
   */
  @Transactional(readOnly = true)
  public MerchantPaymentItem getOne(Long merchantId, Long paymentId) {
    Payment payment =
        paymentRepository
            .findByIdAndMerchantId(paymentId, merchantId)
            .filter(p -> SALES_STATUSES.contains(p.getStatus()))
            .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    return MerchantPaymentItem.from(payment);
  }
}
