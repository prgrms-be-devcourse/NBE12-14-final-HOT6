package com.back.nbe1214finalhot6.domain.qr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrStatusResponse;
import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;
import com.back.nbe1214finalhot6.domain.qr.exception.QrErrorCode;
import com.back.nbe1214finalhot6.domain.qr.repository.QrCodeRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
@IntegrationTest
@Transactional
class QrServiceTest {

  @Autowired private QrService qrService;
  @Autowired private QrCodeRepository qrCodeRepository;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private MutableClock clock;

  @Test
  @DisplayName("동적 QR 을 생성하면 요청한 금액과 대기 상태로 저장된다")
  void 동적QR생성시저장() {
    Long merchantId = saveMerchant();

    QrCreateResponse response = qrService.createDynamic(merchantId, new QrCreateRequest(5000L));

    QrCode saved =
        qrCodeRepository.findAll().stream()
            .filter(qr -> qr.getToken().equals(response.token()))
            .findFirst()
            .orElseThrow();
    assertThat(saved.getMerchantId()).isEqualTo(merchantId);
    assertThat(saved.getAmount()).isEqualTo(5000L);
    assertThat(saved.getStatus()).isEqualTo(QrStatus.WAITING);
    assertThat(response.amount()).isEqualTo(5000L);
  }

  @Test
  @DisplayName("만료 시각은 생성 시각 + 3분이고, 시계를 옮기면 그 시각 기준으로 계산된다")
  void 만료시각은생성시각플러스3분() {
    Long merchantId = saveMerchant();
    LocalDateTime first = LocalDateTime.now(clock);

    QrCreateResponse firstResponse = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));
    clock.advance(Duration.ofMinutes(10));
    QrCreateResponse secondResponse =
        qrService.createDynamic(merchantId, new QrCreateRequest(1000L));

    assertThat(firstResponse.expiresAt()).isEqualTo(first.plusMinutes(3));
    assertThat(secondResponse.expiresAt()).isEqualTo(first.plusMinutes(13));
  }

  @Test
  @DisplayName("같은 가맹점에서 여러 번 생성해도 토큰이 서로 다르다")
  void 토큰은매번달라진다() {
    Long merchantId = saveMerchant();

    QrCreateResponse first = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));
    QrCreateResponse second = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));

    assertThat(first.token()).isNotEqualTo(second.token());
  }

  @Test
  @DisplayName("존재하지 않는 가맹점이면 예외가 발생하고 QR 이 저장되지 않는다")
  void 없는가맹점이면예외() {
    long before = qrCodeRepository.count();

    assertThatThrownBy(() -> qrService.createDynamic(Long.MAX_VALUE, new QrCreateRequest(1000L)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND));
    assertThat(qrCodeRepository.count()).isEqualTo(before);
  }

  @Test
  @DisplayName("생성 직후에는 대기, 만료 시각이 지나면 만료로 조회된다")
  void 시간에따라상태가바뀐다() {
    Long merchantId = saveMerchant();
    QrCreateResponse created = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));

    assertThat(qrService.getStatus(created.token()).status()).isEqualTo(QrStatus.WAITING);

    clock.advance(Duration.ofMinutes(2).plusSeconds(59));
    assertThat(qrService.getStatus(created.token()).status()).isEqualTo(QrStatus.WAITING);

    clock.advance(Duration.ofSeconds(1));
    QrStatusResponse expired = qrService.getStatus(created.token());
    assertThat(expired.status()).isEqualTo(QrStatus.EXPIRED);
    assertThat(expired.expiresAt()).isEqualTo(created.expiresAt());
  }

  @Test
  @DisplayName("사용된 QR 은 만료 시각이 지나도 사용됨으로 조회된다")
  void 사용된QR은만료되어도사용됨() {
    Long merchantId = saveMerchant();
    QrCreateResponse created = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));
    QrCode qrCode = qrCodeRepository.findByToken(created.token()).orElseThrow();
    ReflectionTestUtils.setField(qrCode, "status", QrStatus.USED);
    qrCodeRepository.flush();

    assertThat(qrService.getStatus(created.token()).status()).isEqualTo(QrStatus.USED);

    clock.advance(Duration.ofMinutes(10));
    assertThat(qrService.getStatus(created.token()).status()).isEqualTo(QrStatus.USED);
  }

  @Test
  @DisplayName("존재하지 않는 토큰이면 QR_NOT_FOUND 예외가 발생한다")
  void 없는토큰이면예외() {
    assertThatThrownBy(() -> qrService.getStatus("unknown-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_NOT_FOUND));
  }

  private Long saveMerchant() {
    return merchantRepository
        .save(Merchant.create(1L, "QR 테스트 가맹점", "인천광역시 남동구 1", true))
        .getId();
  }
}
