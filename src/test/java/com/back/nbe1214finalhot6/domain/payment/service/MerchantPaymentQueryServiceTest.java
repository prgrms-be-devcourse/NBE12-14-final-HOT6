package com.back.nbe1214finalhot6.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.payment.dto.MerchantPaymentItem;
import com.back.nbe1214finalhot6.domain.payment.dto.MerchantPaymentsResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.exception.PaymentErrorCode;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.MerchantSales;
import com.back.nbe1214finalhot6.domain.settlement.repository.MerchantSalesRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사장님 결제 내역 서비스 테스트 (PAY-04, 이슈 #201). 시계는 2026-11-18 09:00 KST에서 시작한다.
 *
 * <p>결제 행은 결제 흐름을 거치지 않고 원하는 시각·상태로 직접 저장해서 집계 기준을 검증한다.
 */
@IntegrationTest
class MerchantPaymentQueryServiceTest {

  private static final Long MERCHANT = 1L;
  private static final Long OTHER_MERCHANT = 2L;
  private static final LocalDate D18 = LocalDate.of(2026, 11, 18);

  @Autowired MerchantPaymentQueryService queryService;
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantSalesRepository merchantSalesRepository; // 일 정산이 쓰는 집계 쿼리

  private int seq = 0;

  @AfterEach
  void cleanUp() {
    paymentRepository.deleteAll();
  }

  @Test
  @DisplayName("내 가맹점의 결제만 조회되고 다른 가맹점의 결제는 섞이지 않는다")
  void 내_가맹점만() {
    // given
    Payment mine = completed(MERCHANT, 10_000L, at(D18, 10, 0));
    completed(OTHER_MERCHANT, 99_000L, at(D18, 11, 0));

    // when
    MerchantPaymentsResponse response = queryService.get(MERCHANT, D18, D18, 0, 20);

    // then : 요약과 목록 모두 내 가맹점의 1건뿐이다
    assertThat(response.paymentCount()).isEqualTo(1);
    assertThat(response.paymentAmount()).isEqualTo(10_000L);
    assertThat(response.payments().content())
        .extracting(MerchantPaymentItem::paymentId)
        .containsExactly(mine.getId());
  }

  @Test
  @DisplayName("결제, 취소, 순매출(결제 - 취소)을 집계한다")
  void 요약_집계() {
    // given : 11/18에 결제 3건(10,000 · 20,000 · 30,000)이고 그중 20,000원은 같은 날 취소했다
    completed(MERCHANT, 10_000L, at(D18, 9, 0));
    canceled(MERCHANT, 20_000L, at(D18, 10, 0), at(D18, 15, 0));
    completed(MERCHANT, 30_000L, at(D18, 11, 0));

    // when
    MerchantPaymentsResponse response = queryService.get(MERCHANT, D18, D18, 0, 20);

    // then : 취소된 건도 결제는 완료한 날에 센다
    assertThat(response.paymentCount()).isEqualTo(3);
    assertThat(response.paymentAmount()).isEqualTo(60_000L);
    assertThat(response.cancelCount()).isEqualTo(1);
    assertThat(response.cancelAmount()).isEqualTo(20_000L);
    assertThat(response.netSales()).isEqualTo(40_000L);
  }

  @Test
  @DisplayName("처리 중·실패한 결제는 집계와 목록에서 빠진다")
  void 완료되지_않은_결제_제외() {
    completed(MERCHANT, 10_000L, at(D18, 9, 0));
    unfinished(MERCHANT, 5_000L, PaymentStatus.PROCESSING);
    unfinished(MERCHANT, 7_000L, PaymentStatus.FAILED);

    MerchantPaymentsResponse response = queryService.get(MERCHANT, D18, D18, 0, 20);

    assertThat(response.paymentCount()).isEqualTo(1);
    assertThat(response.paymentAmount()).isEqualTo(10_000L);
    assertThat(response.payments().totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("결제는 완료한 날에, 취소는 취소한 날에 반영된다 (순매출이 음수일 수 있다)")
  void 날짜_기준() {
    // given : 11/17에 결제하고 11/18에 취소했다
    Payment payment = canceled(MERCHANT, 10_000L, at(D18.minusDays(1), 20, 0), at(D18, 9, 0));

    // when
    MerchantPaymentsResponse day17 = queryService.get(MERCHANT, D18.minusDays(1), D18.minusDays(1), 0, 20);
    MerchantPaymentsResponse day18 = queryService.get(MERCHANT, D18, D18, 0, 20);

    // then : 17일은 결제만, 18일은 취소만 잡힌다. 취소한 날의 순매출은 음수다
    assertThat(day17.paymentCount()).isEqualTo(1);
    assertThat(day17.cancelCount()).isZero();
    assertThat(day17.netSales()).isEqualTo(10_000L);
    assertThat(day18.paymentCount()).isZero();
    assertThat(day18.cancelCount()).isEqualTo(1);
    assertThat(day18.cancelAmount()).isEqualTo(10_000L);
    assertThat(day18.netSales()).isEqualTo(-10_000L);
    // 취소한 날 목록에는 그 결제가 취소 상태로 나온다
    assertThat(day18.payments().content())
        .extracting(MerchantPaymentItem::paymentId)
        .containsExactly(payment.getId());
    assertThat(day18.payments().content().get(0).status()).isEqualTo(PaymentStatus.CANCELED);
  }

  @Test
  @DisplayName("자정 경계: 23:59:59는 그날, 다음 날 00:00:00은 다음 날이다")
  void 자정_경계() {
    completed(MERCHANT, 1_000L, D18.atTime(23, 59, 59));
    completed(MERCHANT, 2_000L, D18.plusDays(1).atStartOfDay());

    assertThat(queryService.get(MERCHANT, D18, D18, 0, 20).paymentAmount()).isEqualTo(1_000L);
    assertThat(queryService.get(MERCHANT, D18.plusDays(1), D18.plusDays(1), 0, 20).paymentAmount())
        .isEqualTo(2_000L);
    assertThat(queryService.get(MERCHANT, D18, D18.plusDays(1), 0, 20).paymentAmount())
        .isEqualTo(3_000L);
  }

  @Test
  @DisplayName("일 정산이 쓰는 집계와 같은 금액이 나온다")
  void 일정산과_일치() {
    // given : 결제 2건과 11/17 결제를 11/18에 취소한 1건
    completed(MERCHANT, 10_000L, at(D18, 9, 0));
    completed(MERCHANT, 20_000L, at(D18, 10, 0));
    canceled(MERCHANT, 5_000L, at(D18.minusDays(1), 22, 0), at(D18, 12, 0));
    LocalDateTime start = D18.atStartOfDay();
    LocalDateTime end = D18.plusDays(1).atStartOfDay();

    // when
    MerchantPaymentsResponse response = queryService.get(MERCHANT, D18, D18, 0, 20);

    // then : 일 정산의 결제 합계와 취소 합계와 같다
    long settlementSales =
        total(merchantSalesRepository.sumByCompletedAt(start, end, List.of(PaymentStatus.COMPLETED, PaymentStatus.CANCELED)));
    long settlementCancels =
        total(merchantSalesRepository.sumByCanceledAt(start, end, PaymentStatus.CANCELED));
    assertThat(response.paymentAmount()).isEqualTo(settlementSales);
    assertThat(response.cancelAmount()).isEqualTo(settlementCancels);
    assertThat(response.netSales()).isEqualTo(settlementSales - settlementCancels);
  }

  @Test
  @DisplayName("목록은 최신순이고, 같은 시각이면 나중에 만든 결제가 먼저 온다")
  void 최신순() {
    Payment first = completed(MERCHANT, 1_000L, at(D18, 9, 0));
    Payment third = completed(MERCHANT, 3_000L, at(D18, 11, 0));
    Payment second = completed(MERCHANT, 2_000L, at(D18, 10, 0));
    Payment sameTime = completed(MERCHANT, 4_000L, at(D18, 11, 0));

    List<MerchantPaymentItem> content = queryService.get(MERCHANT, D18, D18, 0, 20).payments().content();

    assertThat(content)
        .extracting(MerchantPaymentItem::paymentId)
        .containsExactly(sameTime.getId(), third.getId(), second.getId(), first.getId());
  }

  @Test
  @DisplayName("페이지 크기만큼 나누고, 100을 넘는 크기는 100으로 줄인다")
  void 페이징() {
    for (int i = 0; i < 25; i++) {
      completed(MERCHANT, 1_000L, at(D18, 9, 0).plusMinutes(i));
    }

    MerchantPaymentsResponse first = queryService.get(MERCHANT, D18, D18, 0, 10);
    MerchantPaymentsResponse last = queryService.get(MERCHANT, D18, D18, 2, 10);
    MerchantPaymentsResponse capped = queryService.get(MERCHANT, D18, D18, 0, 1_000);

    assertThat(first.payments().content()).hasSize(10);
    assertThat(last.payments().content()).hasSize(5);
    assertThat(first.payments().totalElements()).isEqualTo(25);
    assertThat(first.payments().totalPages()).isEqualTo(3);
    assertThat(capped.payments().size()).isEqualTo(100);
    // 요약은 페이지와 상관없이 기간 전체다
    assertThat(first.paymentCount()).isEqualTo(25);
    assertThat(last.paymentAmount()).isEqualTo(25_000L);
  }

  @Test
  @DisplayName("기간을 생략하면 오늘, from 만 주면 그날 하루를 조회한다")
  void 기간_기본값() {
    completed(MERCHANT, 10_000L, at(D18, 9, 0));
    completed(MERCHANT, 20_000L, at(D18.minusDays(1), 9, 0));

    MerchantPaymentsResponse today = queryService.get(MERCHANT, null, null, 0, 20); // 시계는 11/18
    MerchantPaymentsResponse onlyFrom = queryService.get(MERCHANT, D18.minusDays(1), null, 0, 20);

    assertThat(today.from()).isEqualTo(D18);
    assertThat(today.to()).isEqualTo(D18);
    assertThat(today.paymentAmount()).isEqualTo(10_000L);
    assertThat(onlyFrom.from()).isEqualTo(D18.minusDays(1));
    assertThat(onlyFrom.to()).isEqualTo(D18.minusDays(1));
    assertThat(onlyFrom.paymentAmount()).isEqualTo(20_000L);
  }

  @Test
  @DisplayName("거래가 없으면 0과 빈 목록이다")
  void 빈_기간() {
    MerchantPaymentsResponse response = queryService.get(MERCHANT, D18, D18, 0, 20);

    assertThat(response.paymentCount()).isZero();
    assertThat(response.paymentAmount()).isZero();
    assertThat(response.cancelAmount()).isZero();
    assertThat(response.netSales()).isZero();
    assertThat(response.payments().content()).isEmpty();
  }

  @Test
  @DisplayName("시작이 끝보다 늦거나 31일을 넘으면 PAY-008, 31일은 허용한다")
  void 기간_검증() {
    assertThatThrownBy(() -> queryService.get(MERCHANT, D18, D18.minusDays(1), 0, 20))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_INVALID_PERIOD);
    assertThatThrownBy(() -> queryService.get(MERCHANT, D18, D18.plusDays(31), 0, 20)) // 32일
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_INVALID_PERIOD);
    assertThatCode(() -> queryService.get(MERCHANT, D18, D18.plusDays(30), 0, 20)) // 31일
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("결제 번호로 내 가맹점의 결제 한 건을 조회한다")
  void 한건_조회() {
    // given
    Payment payment = completed(MERCHANT, 10_000L, at(D18, 10, 0));

    // when
    MerchantPaymentItem item = queryService.getOne(MERCHANT, payment.getId());

    // then
    assertThat(item.paymentId()).isEqualTo(payment.getId());
    assertThat(item.status()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(item.amount()).isEqualTo(10_000L);
    assertThat(item.walletUsedAmount()).isEqualTo(10_000L);
    assertThat(item.cashbackUsedAmount()).isZero();
    assertThat(item.completedAt()).isEqualTo(at(D18, 10, 0));
    assertThat(item.canceledAt()).isNull();
  }

  @Test
  @DisplayName("취소된 결제는 취소 시각과 함께 조회된다")
  void 취소된_결제_조회() {
    // given
    Payment payment = canceled(MERCHANT, 20_000L, at(D18, 10, 0), at(D18, 15, 0));

    // when
    MerchantPaymentItem item = queryService.getOne(MERCHANT, payment.getId());

    // then
    assertThat(item.status()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(item.completedAt()).isEqualTo(at(D18, 10, 0));
    assertThat(item.canceledAt()).isEqualTo(at(D18, 15, 0));
  }

  @Test
  @DisplayName("다른 가맹점의 결제 번호로 조회하면 PAY-003 이다")
  void 다른_가맹점의_결제_조회_거절() {
    // given
    Payment others = completed(OTHER_MERCHANT, 99_000L, at(D18, 11, 0));

    // then : 없는 번호와 똑같이 응답해서 남의 결제가 있는지 알 수 없다
    assertNotFound(MERCHANT, others.getId());
  }

  @Test
  @DisplayName("없는 결제 번호로 조회하면 PAY-003 이다")
  void 없는_번호_조회_거절() {
    assertNotFound(MERCHANT, 999_999L);
  }

  @Test
  @DisplayName("처리 중이거나 실패한 결제는 목록처럼 조회되지 않고 PAY-003 이다")
  void 완료되지_않은_결제_조회_거절() {
    // given
    Payment processing = unfinished(MERCHANT, 5_000L, PaymentStatus.PROCESSING);
    Payment failed = unfinished(MERCHANT, 7_000L, PaymentStatus.FAILED);

    // then
    assertNotFound(MERCHANT, processing.getId());
    assertNotFound(MERCHANT, failed.getId());
  }

  private void assertNotFound(Long merchantId, Long paymentId) {
    assertThatThrownBy(() -> queryService.getOne(merchantId, paymentId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
  }

  private static long total(List<MerchantSales> sales) {
    return sales.stream().filter(s -> MERCHANT.equals(s.merchantId())).mapToLong(MerchantSales::total).sum();
  }

  private static LocalDateTime at(LocalDate date, int hour, int minute) {
    return date.atTime(hour, minute);
  }

  private Payment completed(Long merchantId, long amount, LocalDateTime completedAt) {
    return save(merchantId, amount, PaymentStatus.COMPLETED, completedAt, null);
  }

  private Payment canceled(Long merchantId, long amount, LocalDateTime completedAt, LocalDateTime canceledAt) {
    return save(merchantId, amount, PaymentStatus.CANCELED, completedAt, canceledAt);
  }

  // 처리 중·실패 건은 완료 시각이 없다
  private Payment unfinished(Long merchantId, long amount, PaymentStatus status) {
    return save(merchantId, amount, status, null, null);
  }

  private Payment save(
      Long merchantId,
      long amount,
      PaymentStatus status,
      LocalDateTime completedAt,
      LocalDateTime canceledAt) {
    return paymentRepository.save(
        Payment.builder()
            .walletId(1L)
            .merchantId(merchantId)
            .qrToken("qr-" + seq)
            .idempotencyKey("key-" + seq++)
            .amount(amount)
            .walletUsedAmount(amount)
            .cashbackUsedAmount(0L)
            .status(status)
            .requestedAt(completedAt != null ? completedAt : D18.atTime(8, 0))
            .completedAt(completedAt)
            .canceledAt(canceledAt)
            .build());
  }
}
