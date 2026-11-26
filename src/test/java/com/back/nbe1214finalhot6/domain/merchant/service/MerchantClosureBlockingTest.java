package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateResponse;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
@IntegrationTest
@Transactional
class MerchantClosureBlockingTest {

  private static final String KEYWORD = "hz3m";

  @Autowired private MerchantService merchantService;
  @Autowired private QrService qrService;
  @Autowired private MerchantRepository merchantRepository;

  @Test
  @DisplayName("영업 중인 가맹점은 assertActive 를 통과한다")
  void 영업중_통과() {
    Long merchantId = saveMerchant(1L);

    merchantService.assertActive(merchantId);
  }

  @Test
  @DisplayName("해지 진행 중이면 MER-003, 해지됐으면 MER-004, 없으면 MER-001 로 거절한다")
  void 상태별_거절_사유() {
    Long closing = saveMerchant(2L);
    changeStatus(closing, MerchantStatus.CLOSING);
    Long closed = saveMerchant(3L);
    changeStatus(closed, MerchantStatus.CLOSED);

    assertCode(() -> merchantService.assertActive(closing), MerchantErrorCode.MERCHANT_CLOSING);
    assertCode(() -> merchantService.assertActive(closed), MerchantErrorCode.MERCHANT_CLOSED);
    assertCode(
        () -> merchantService.assertActive(Long.MAX_VALUE), MerchantErrorCode.MERCHANT_NOT_FOUND);
  }

  @Test
  @DisplayName("해지를 신청한 가맹점은 동적 QR 을 만들 수 없다")
  void QR_생성_차단() {
    Long merchantId = saveMerchant(4L);
    changeStatus(merchantId, MerchantStatus.CLOSING);

    assertCode(
        () -> qrService.createDynamic(merchantId, new QrCreateRequest(1000L)),
        MerchantErrorCode.MERCHANT_CLOSING);
  }

  @Test
  @DisplayName("해지를 신청하기 전에 만든 QR 도 스캔 조회가 막힌다")
  void QR_스캔_차단() {
    Long merchantId = saveMerchant(5L);
    QrCreateResponse qr = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));
    changeStatus(merchantId, MerchantStatus.CLOSING);

    assertCode(() -> qrService.scan(qr.token()), MerchantErrorCode.MERCHANT_CLOSING);
  }

  @Test
  @DisplayName("해지를 신청하기 전에 만든 QR 의 결제 대상 조회도 막혀 새 결제가 시작되지 않는다")
  void 결제_대상_조회_차단() {
    Long merchantId = saveMerchant(6L);
    QrCreateResponse qr = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));
    changeStatus(merchantId, MerchantStatus.CLOSED);

    assertCode(() -> qrService.getPaymentTarget(qr.token()), MerchantErrorCode.MERCHANT_CLOSED);
  }

  @Test
  @DisplayName("영업 중인 가맹점의 QR 은 결제 대상 조회가 통과한다")
  void 영업중_결제_대상_조회() {
    Long merchantId = saveMerchant(7L);
    QrCreateResponse qr = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));

    assertThat(qrService.getPaymentTarget(qr.token()).merchantId()).isEqualTo(merchantId);
  }

  @Test
  @DisplayName("이름 검색에는 영업 중인 가맹점만 나온다")
  void 검색_영업중만() {
    saveMerchant(8L, KEYWORD + " 영업점");
    Long closing = saveMerchant(9L, KEYWORD + " 해지중");
    changeStatus(closing, MerchantStatus.CLOSING);
    Long closed = saveMerchant(10L, KEYWORD + " 해지됨");
    changeStatus(closed, MerchantStatus.CLOSED);

    var result = merchantService.search(KEYWORD, 0, 20);

    assertThat(result.content()).extracting(MerchantResponse::name).containsExactly(KEYWORD + " 영업점");
  }

  @Test
  @DisplayName("내 가맹점 조회에는 현재 상태가 담긴다")
  void 조회에_상태_포함() {
    Long merchantId = saveMerchant(11L);
    changeStatus(merchantId, MerchantStatus.CLOSING);

    assertThat(merchantService.get(merchantId).status()).isEqualTo(MerchantStatus.CLOSING);
  }

  private Long saveMerchant(Long ownerId) {
    return saveMerchant(ownerId, "차단 테스트 가맹점");
  }

  private Long saveMerchant(Long ownerId, String name) {
    return merchantRepository
        .save(Merchant.create(ownerId + 9_200L, name, "인천광역시 남동구 1", true))
        .getId();
  }

  private void changeStatus(Long merchantId, MerchantStatus to) {
    merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.ACTIVE, to);
  }

  private void assertCode(Runnable action, MerchantErrorCode code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
  }
}
