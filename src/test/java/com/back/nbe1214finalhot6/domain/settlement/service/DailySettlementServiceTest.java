package com.back.nbe1214finalhot6.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 테스트 시계는 2026-11-18 09:00 KST 에서 시작하므로 정산 대상 날짜는 11/17 이전이다.
@IntegrationTest
class DailySettlementServiceTest {

  private static final Long MERCHANT = 1L;
  private static final Long OTHER_MERCHANT = 2L;
  private static final LocalDate D15 = LocalDate.of(2026, 11, 15);
  private static final LocalDate D16 = LocalDate.of(2026, 11, 16);
  private static final LocalDate D17 = LocalDate.of(2026, 11, 17);

  @Autowired DailySettlementService settlementService;
  @Autowired DailySettlementRepository settlementRepository;
  @Autowired PaymentRepository paymentRepository;
  @Autowired MutableClock clock;

  @AfterEach
  void cleanUp() {
    settlementRepository.deleteAll();
    paymentRepository.deleteAll();
  }

  @Test
  @DisplayName("그날 완료된 결제의 합이 순매출로 정산된다")
  void 결제합계_정산() {
    saveCompleted(MERCHANT, 10_000L, at(D17, 10, 0));
    saveCompleted(MERCHANT, 5_000L, at(D17, 15, 30));

    settlementService.settle(D17);

    DailySettlement settlement = find(MERCHANT, D17);
    assertThat(settlement.getPaymentTotal()).isEqualTo(15_000L);
    assertThat(settlement.getCancelTotal()).isZero();
    assertThat(settlement.getConfirmedAmount()).isEqualTo(15_000L);
    assertThat(settlement.getCarryOut()).isZero();
  }

  @Test
  @DisplayName("같은 날짜를 다시 실행해도 정산은 한 건이고 두 번째 실행은 건너뛴다")
  void 재실행_중복방지() {
    saveCompleted(MERCHANT, 10_000L, at(D17, 10, 0));

    SettlementRunResult first = settlementService.settle(D17);
    SettlementRunResult second = settlementService.settle(D17);

    assertThat(first.created()).isEqualTo(1);
    assertThat(second.created()).isZero();
    assertThat(second.skipped()).isEqualTo(1);
    assertThat(settlementRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("취소는 결제한 날의 합계를 유지하고 취소한 날의 매출에서만 차감한다")
  void 취소는_취소한날에_반영() {
    saveCanceled(MERCHANT, 30_000L, at(D15, 10, 0), at(D16, 11, 0));

    settlementService.settle(D15);
    settlementService.settle(D16);

    DailySettlement paidDay = find(MERCHANT, D15);
    assertThat(paidDay.getPaymentTotal()).isEqualTo(30_000L);
    assertThat(paidDay.getCancelTotal()).isZero();
    assertThat(paidDay.getConfirmedAmount()).isEqualTo(30_000L);

    DailySettlement canceledDay = find(MERCHANT, D16);
    assertThat(canceledDay.getPaymentTotal()).isZero();
    assertThat(canceledDay.getCancelTotal()).isEqualTo(30_000L);
    assertThat(canceledDay.getNetSales()).isEqualTo(-30_000L);
  }

  @Test
  @DisplayName("순매출이 음수인 날은 0원으로 정산하고 부족분을 이월한다")
  void 음수순매출_이월() {
    saveCanceled(MERCHANT, 30_000L, at(D15, 10, 0), at(D16, 11, 0));

    settlementService.settle(D15);
    settlementService.settle(D16);

    DailySettlement settlement = find(MERCHANT, D16);
    assertThat(settlement.getConfirmedAmount()).isZero();
    assertThat(settlement.getCarryOut()).isEqualTo(-30_000L);
  }

  @Test
  @DisplayName("이월된 부족분은 다음 양수 매출에서 먼저 차감된다")
  void 이월_상계() {
    saveCanceled(MERCHANT, 30_000L, at(D15, 10, 0), at(D16, 11, 0));
    saveCompleted(MERCHANT, 50_000L, at(D17, 10, 0));

    settlementService.settle(D15);
    settlementService.settle(D16);
    settlementService.settle(D17);

    DailySettlement settlement = find(MERCHANT, D17);
    assertThat(settlement.getCarryIn()).isEqualTo(-30_000L);
    assertThat(settlement.getConfirmedAmount()).isEqualTo(20_000L);
    assertThat(settlement.getCarryOut()).isZero();
  }

  @Test
  @DisplayName("매출이 없어도 이월 부족분이 남은 가맹점은 계속 정산 대상이다")
  void 매출없어도_이월유지() {
    saveCanceled(MERCHANT, 30_000L, at(D15, 10, 0), at(D16, 11, 0));

    settlementService.settle(D15);
    settlementService.settle(D16);
    settlementService.settle(D17);

    DailySettlement settlement = find(MERCHANT, D17);
    assertThat(settlement.getPaymentTotal()).isZero();
    assertThat(settlement.getCarryIn()).isEqualTo(-30_000L);
    assertThat(settlement.getCarryOut()).isEqualTo(-30_000L);
  }

  @Test
  @DisplayName("자정 직전은 그날, 자정 정각은 다음 날 정산에 들어간다")
  void 날짜경계() {
    saveCompleted(MERCHANT, 1_000L, LocalDateTime.of(2026, 11, 16, 23, 59, 59));
    saveCompleted(MERCHANT, 2_000L, LocalDateTime.of(2026, 11, 17, 0, 0, 0));

    settlementService.settle(D16);
    settlementService.settle(D17);

    assertThat(find(MERCHANT, D16).getPaymentTotal()).isEqualTo(1_000L);
    assertThat(find(MERCHANT, D17).getPaymentTotal()).isEqualTo(2_000L);
  }

  @Test
  @DisplayName("처리 중이거나 실패한 결제는 정산에 포함되지 않는다")
  void 미완료결제_제외() {
    savePayment(MERCHANT, 10_000L, PaymentStatus.PROCESSING, null, null);
    savePayment(MERCHANT, 20_000L, PaymentStatus.FAILED, null, null);

    SettlementRunResult result = settlementService.settle(D17);

    assertThat(result.created()).isZero();
    assertThat(settlementRepository.count()).isZero();
  }

  @Test
  @DisplayName("정산 이후에 늦게 완료된 결제는 확정된 정산을 바꾸지 않는다")
  void 정산후_늦은결제는_정산을_바꾸지_않음() {
    saveCompleted(MERCHANT, 10_000L, at(D17, 10, 0));
    settlementService.settle(D17);

    saveCompleted(MERCHANT, 7_000L, at(D17, 23, 0));
    settlementService.settle(D17);

    DailySettlement settlement = find(MERCHANT, D17);
    assertThat(settlement.getPaymentTotal()).isEqualTo(10_000L);
    assertThat(settlement.getConfirmedAmount()).isEqualTo(10_000L);
  }

  @Test
  @DisplayName("가맹점마다 따로 정산한다")
  void 가맹점별_정산() {
    saveCompleted(MERCHANT, 10_000L, at(D17, 10, 0));
    saveCompleted(OTHER_MERCHANT, 3_000L, at(D17, 11, 0));

    SettlementRunResult result = settlementService.settle(D17);

    assertThat(result.created()).isEqualTo(2);
    assertThat(find(MERCHANT, D17).getConfirmedAmount()).isEqualTo(10_000L);
    assertThat(find(OTHER_MERCHANT, D17).getConfirmedAmount()).isEqualTo(3_000L);
  }

  @Test
  @DisplayName("오늘이나 미래 날짜는 정산할 수 없고, 하루가 지나면 정산할 수 있다")
  void 오늘이후_정산불가() {
    assertThatThrownBy(() -> settlementService.settle(LocalDate.of(2026, 11, 18)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(SettlementErrorCode.SETTLEMENT_DATE_NOT_ALLOWED));

    clock.advance(Duration.ofDays(1));

    assertThat(settlementService.settle(LocalDate.of(2026, 11, 18)).failed()).isZero();
  }

  @Test
  @DisplayName("정산을 건너뛴 날이 있으면 가장 최근 정산일부터 어제까지 이어서 채우고 이월을 잇는다")
  void 건너뛴날_따라잡기() {
    saveCanceled(MERCHANT, 30_000L, at(D15, 10, 0), at(D16, 11, 0));
    settlementService.settle(D15);

    settlementService.settleUntil(D17);

    DailySettlement skipped = find(MERCHANT, D16);
    assertThat(skipped.getCarryOut()).isEqualTo(-30_000L);
    DailySettlement last = find(MERCHANT, D17);
    assertThat(last.getCarryIn()).isEqualTo(-30_000L);
    assertThat(last.getCarryOut()).isEqualTo(-30_000L);
  }

  @Test
  @DisplayName("정산 이력이 없으면 어제 하루만 정산한다")
  void 이력없으면_어제만() {
    saveCompleted(MERCHANT, 1_000L, at(D16, 10, 0));
    saveCompleted(MERCHANT, 2_000L, at(D17, 10, 0));

    settlementService.settleUntil(D17);

    assertThat(settlementRepository.findByMerchantIdAndSettlementDate(MERCHANT, D16)).isEmpty();
    assertThat(find(MERCHANT, D17).getPaymentTotal()).isEqualTo(2_000L);
  }

  @Test
  @DisplayName("이미 어제까지 정산되어 있으면 다시 실행해도 정산은 늘지 않는다")
  void 따라잡기_재실행() {
    saveCompleted(MERCHANT, 1_000L, at(D17, 10, 0));
    settlementService.settleUntil(D17);

    settlementService.settleUntil(D17);

    assertThat(settlementRepository.count()).isEqualTo(1);
  }

  private DailySettlement find(Long merchantId, LocalDate date) {
    return settlementRepository.findByMerchantIdAndSettlementDate(merchantId, date).orElseThrow();
  }

  private LocalDateTime at(LocalDate date, int hour, int minute) {
    return date.atTime(hour, minute);
  }

  private void saveCompleted(Long merchantId, long amount, LocalDateTime completedAt) {
    savePayment(merchantId, amount, PaymentStatus.COMPLETED, completedAt, null);
  }

  private void saveCanceled(
      Long merchantId, long amount, LocalDateTime completedAt, LocalDateTime canceledAt) {
    savePayment(merchantId, amount, PaymentStatus.CANCELED, completedAt, canceledAt);
  }

  private void savePayment(
      Long merchantId,
      long amount,
      PaymentStatus status,
      LocalDateTime completedAt,
      LocalDateTime canceledAt) {
    paymentRepository.save(
        Payment.builder()
            .walletId(1L)
            .merchantId(merchantId)
            .idempotencyKey(UUID.randomUUID().toString())
            .amount(amount)
            .walletUsedAmount(amount)
            .cashbackUsedAmount(0L)
            .status(status)
            .requestedAt(completedAt != null ? completedAt : LocalDateTime.of(2026, 11, 17, 9, 0))
            .completedAt(completedAt)
            .canceledAt(canceledAt)
            .build());
  }
}
