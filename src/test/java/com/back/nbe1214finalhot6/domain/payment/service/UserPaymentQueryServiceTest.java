package com.back.nbe1214finalhot6.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.payment.dto.UserPaymentItem;
import com.back.nbe1214finalhot6.domain.payment.dto.UserPaymentsResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.Payment;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.repository.PaymentRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 이용자 결제 내역 서비스 테스트 (이슈 #215). 결제 행은 원하는 상태·시각으로 직접 저장한다. */
@IntegrationTest
class UserPaymentQueryServiceTest {

  private static final Long MY_WALLET = 1L;
  private static final Long OTHER_WALLET = 2L;

  @Autowired UserPaymentQueryService queryService;
  @Autowired PaymentRepository paymentRepository;
  @Autowired MerchantRepository merchantRepository;

  private Long cafeId;
  private int seq = 0;

  @BeforeEach
  void setUp() {
    cafeId = merchantRepository.save(Merchant.create(7_001L, "테스트 카페", "서울", true)).getId();
  }

  @AfterEach
  void cleanUp() {
    paymentRepository.deleteAll();
    merchantRepository.deleteAll();
  }

  @Test
  @DisplayName("내 지갑의 결제만 조회되고 다른 이용자의 결제는 섞이지 않는다")
  void 내_결제만() {
    Payment mine = save(MY_WALLET, cafeId, PaymentStatus.COMPLETED, at(10), null);
    save(OTHER_WALLET, cafeId, PaymentStatus.COMPLETED, at(11), null);

    UserPaymentsResponse response = queryService.get(MY_WALLET, null, 0, 10);

    assertThat(response.payments().content())
        .extracting(UserPaymentItem::paymentId)
        .containsExactly(mine.getId());
    assertThat(response.payments().totalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("최신 완료 순으로 정렬되고 취소된 결제도 함께 보인다")
  void 최신순_취소_포함() {
    Payment old = save(MY_WALLET, cafeId, PaymentStatus.COMPLETED, at(9), null);
    Payment canceled = save(MY_WALLET, cafeId, PaymentStatus.CANCELED, at(10), at(12));
    Payment latest = save(MY_WALLET, cafeId, PaymentStatus.COMPLETED, at(11), null);

    UserPaymentsResponse response = queryService.get(MY_WALLET, null, 0, 10);

    assertThat(response.payments().content())
        .extracting(UserPaymentItem::paymentId)
        .containsExactly(latest.getId(), canceled.getId(), old.getId());
    assertThat(response.payments().content().get(1).status()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(response.payments().content().get(1).canceledAt()).isEqualTo(at(12));
  }

  @Test
  @DisplayName("처리 중·실패한 결제는 목록에서 빠진다")
  void 완료되지_않은_결제_제외() {
    Payment done = save(MY_WALLET, cafeId, PaymentStatus.COMPLETED, at(9), null);
    save(MY_WALLET, cafeId, PaymentStatus.PROCESSING, null, null);
    save(MY_WALLET, cafeId, PaymentStatus.FAILED, null, null);

    UserPaymentsResponse response = queryService.get(MY_WALLET, null, 0, 10);

    assertThat(response.payments().content())
        .extracting(UserPaymentItem::paymentId)
        .containsExactly(done.getId());
  }

  @Test
  @DisplayName("status 로 완료 또는 취소만 골라 볼 수 있다")
  void 상태_필터() {
    Payment completed = save(MY_WALLET, cafeId, PaymentStatus.COMPLETED, at(9), null);
    Payment canceled = save(MY_WALLET, cafeId, PaymentStatus.CANCELED, at(10), at(11));

    assertThat(queryService.get(MY_WALLET, PaymentStatus.COMPLETED, 0, 10).payments().content())
        .extracting(UserPaymentItem::paymentId)
        .containsExactly(completed.getId());
    assertThat(queryService.get(MY_WALLET, PaymentStatus.CANCELED, 0, 10).payments().content())
        .extracting(UserPaymentItem::paymentId)
        .containsExactly(canceled.getId());
  }

  @Test
  @DisplayName("완료·취소가 아닌 status 를 요청하면 COM-001")
  void 잘못된_상태() {
    assertThatThrownBy(() -> queryService.get(MY_WALLET, PaymentStatus.PROCESSING, 0, 10))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_REQUEST);
    assertThatThrownBy(() -> queryService.get(MY_WALLET, PaymentStatus.FAILED, 0, 10))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("가맹점명과 금액 정보가 응답에 담긴다")
  void 가맹점명_매핑() {
    save(MY_WALLET, cafeId, PaymentStatus.COMPLETED, at(9), null);

    UserPaymentItem item = queryService.get(MY_WALLET, null, 0, 10).payments().content().get(0);

    assertThat(item.merchantName()).isEqualTo("테스트 카페");
    assertThat(item.amount()).isEqualTo(10_000L);
    assertThat(item.walletUsedAmount()).isEqualTo(10_000L);
    assertThat(item.cashbackUsedAmount()).isZero();
    assertThat(item.completedAt()).isEqualTo(at(9));
  }

  @Test
  @DisplayName("가맹점을 찾지 못해도 조회는 되고 가맹점명만 null 이다")
  void 가맹점_없음() {
    save(MY_WALLET, 99_999L, PaymentStatus.COMPLETED, at(9), null);

    UserPaymentItem item = queryService.get(MY_WALLET, null, 0, 10).payments().content().get(0);

    assertThat(item.merchantName()).isNull();
  }

  @Test
  @DisplayName("페이지로 나눠서 조회하고 size 는 100 건으로 제한한다")
  void 페이징() {
    for (int i = 0; i < 5; i++) {
      save(MY_WALLET, cafeId, PaymentStatus.COMPLETED, at(9).plusMinutes(i), null);
    }

    UserPaymentsResponse second = queryService.get(MY_WALLET, null, 1, 2);
    UserPaymentsResponse huge = queryService.get(MY_WALLET, null, 0, 1_000);

    assertThat(second.payments().content()).hasSize(2);
    assertThat(second.payments().totalElements()).isEqualTo(5);
    assertThat(second.payments().totalPages()).isEqualTo(3);
    assertThat(huge.payments().size()).isEqualTo(UserPaymentQueryService.MAX_PAGE_SIZE);
  }

  @Test
  @DisplayName("결제가 없으면 빈 목록이다")
  void 빈_목록() {
    UserPaymentsResponse response = queryService.get(MY_WALLET, null, 0, 10);

    assertThat(response.payments().content()).isEmpty();
    assertThat(response.payments().totalElements()).isZero();
  }

  private static LocalDateTime at(int hour) {
    return LocalDateTime.of(2026, 11, 25, hour, 0);
  }

  private Payment save(
      Long walletId,
      Long merchantId,
      PaymentStatus status,
      LocalDateTime completedAt,
      LocalDateTime canceledAt) {
    return paymentRepository.save(
        Payment.builder()
            .walletId(walletId)
            .merchantId(merchantId)
            .qrToken("qr-" + seq)
            .idempotencyKey("key-" + seq++)
            .amount(10_000L)
            .walletUsedAmount(10_000L)
            .cashbackUsedAmount(0L)
            .status(status)
            .requestedAt(completedAt != null ? completedAt : at(8))
            .completedAt(completedAt)
            .canceledAt(canceledAt)
            .build());
  }
}
