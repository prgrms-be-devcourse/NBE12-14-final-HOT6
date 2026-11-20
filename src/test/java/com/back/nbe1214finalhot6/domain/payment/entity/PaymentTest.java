package com.back.nbe1214finalhot6.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

  private Payment payment(PaymentStatus status) {
    return Payment.builder()
        .walletId(1L)
        .merchantId(1L)
        .qrToken("qr-token-1")
        .idempotencyKey("key-1")
        .amount(12_000L)
        .walletUsedAmount(0L)
        .cashbackUsedAmount(0L)
        .status(status)
        .requestedAt(LocalDateTime.of(2026, 11, 19, 10, 0))
        .build();
  }

  @Test
  @DisplayName("처리 중인 결제를 실패 처리하면 FAILED 가 되고 실패 코드가 기록된다")
  void 처리중결제를실패처리() {
    Payment payment = payment(PaymentStatus.PROCESSING);

    payment.failure("QR-003");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureCode()).isEqualTo("QR-003");
  }

  @Test
  @DisplayName("실패 처리는 취소 정보를 건드리지 않는다")
  void 실패처리는취소정보를건드리지않는다() {
    Payment payment = payment(PaymentStatus.PROCESSING);

    payment.failure("PAY-002");

    assertThat(payment.getCanceledAt()).isNull();
    assertThat(payment.getCanceledBy()).isNull();
    assertThat(payment.getCompletedAt()).isNull();
  }

  @Test
  @DisplayName("처리 중이 아닌 결제는 실패 처리할 수 없다 (완료된 결제가 FAILED 로 바뀌면 안 된다)")
  void 처리중이아니면실패처리불가() {
    for (PaymentStatus status :
        new PaymentStatus[] {PaymentStatus.COMPLETED, PaymentStatus.FAILED, PaymentStatus.CANCELED}) {
      Payment payment = payment(status);

      assertThatThrownBy(() -> payment.failure("COM-010"))
          .isInstanceOf(IllegalStateException.class);
      assertThat(payment.getStatus()).isEqualTo(status);
      assertThat(payment.getFailureCode()).isNull();
    }
  }

  @Test
  @DisplayName("실패 코드가 비어 있으면 거부하고 상태를 바꾸지 않는다")
  void 실패코드가비면거부() {
    Payment payment = payment(PaymentStatus.PROCESSING);

    assertThatThrownBy(() -> payment.failure(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> payment.failure(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
  }
}
