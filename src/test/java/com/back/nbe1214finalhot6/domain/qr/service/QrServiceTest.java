package com.back.nbe1214finalhot6.domain.qr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrPaymentTarget;
import com.back.nbe1214finalhot6.domain.qr.dto.QrScanResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrStatusResponse;
import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;
import com.back.nbe1214finalhot6.domain.qr.exception.QrErrorCode;
import com.back.nbe1214finalhot6.domain.qr.repository.QrCodeRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// docker compose 의 PostgreSQL 이 켜져 있어야 한다. 테스트 전용 DB(devpay_test)를 사용한다.
@IntegrationTest
@Transactional
class QrServiceTest {

  private long nextOwnerId = 5_000L;

  @Autowired private QrService qrService;
  @Autowired private QrCodeRepository qrCodeRepository;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private MutableClock clock;
  @Autowired private EntityManager entityManager;

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
    qrService.markUsed(qrCode.getToken(), LocalDateTime.now(clock));
    entityManager.clear();

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

  @Test
  @DisplayName("조건이 모두 맞으면 사용됨으로 바뀌고 사용 시각이 저장된다")
  void 사용처리성공() {
    QrCode qr = saveQr();
    LocalDateTime now = LocalDateTime.now(clock);

    qrService.markUsed(qr.getToken(), now);
    entityManager.clear();

    QrCode used = qrCodeRepository.findById(qr.getId()).orElseThrow();
    assertThat(used.getStatus()).isEqualTo(QrStatus.USED);
    assertThat(used.getUsedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("만료는 접수 시각 기준이라, 처리가 늦어져도 만료 전에 접수됐다면 성공한다")
  void 접수시각기준으로만료판정() {
    QrCode qr = saveQr();
    LocalDateTime requestedAt = LocalDateTime.now(clock).plusMinutes(2).plusSeconds(59);
    clock.advance(Duration.ofMinutes(10));

    qrService.markUsed(qr.getToken(), requestedAt);
    entityManager.clear();

    assertThat(qrCodeRepository.findById(qr.getId()).orElseThrow().getStatus())
        .isEqualTo(QrStatus.USED);
  }

  @Test
  @DisplayName("접수 시각이 만료 시각 정각이면 QR_EXPIRED 예외가 발생하고 상태는 그대로다")
  void 접수시각이만료시각이면실패() {
    QrCode qr = saveQr();
    LocalDateTime requestedAt = qr.getExpiresAt();

    assertThatThrownBy(() -> qrService.markUsed(qr.getToken(), requestedAt))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_EXPIRED));
    assertThat(qrCodeRepository.findById(qr.getId()).orElseThrow().getStatus())
        .isEqualTo(QrStatus.WAITING);
  }

  @Test
  @DisplayName("이미 사용된 QR 을 같은 값으로 다시 사용 처리하면 QR_ALREADY_USED 예외가 발생한다")
  void 이미사용된QR은사용처리실패() {
    QrCode qr = saveQr();
    LocalDateTime now = LocalDateTime.now(clock);
    qrService.markUsed(qr.getToken(), now);

    assertThatThrownBy(() -> qrService.markUsed(qr.getToken(), now))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_ALREADY_USED));
  }

  @Test
  @DisplayName("사용되고 만료까지 된 QR 이면 만료가 아니라 이미 사용됨 예외가 발생한다")
  void 사용됨이만료보다우선() {
    QrCode qr = saveQr();
    LocalDateTime now = LocalDateTime.now(clock);
    qrService.markUsed(qr.getToken(), now);
    LocalDateTime late = qr.getExpiresAt().plusMinutes(10);

    assertThatThrownBy(() -> qrService.markUsed(qr.getToken(), late))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_ALREADY_USED));
  }

  @Test
  @DisplayName("QR 을 먼저 조회해 둔 트랜잭션에서도 이미 사용된 QR 이면 QR_ALREADY_USED 예외가 발생한다")
  void 먼저조회해둬도이미사용됨예외() {
    QrCode qr = saveQr();
    LocalDateTime now = LocalDateTime.now(clock);
    qrCodeRepository.findByToken(qr.getToken()).orElseThrow();
    qrService.markUsed(qr.getToken(), now);

    assertThatThrownBy(() -> qrService.markUsed(qr.getToken(), now))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_ALREADY_USED));
  }

  @Test
  @DisplayName("접수 시각이 없으면 만료로 처리하지 않고 INVALID_REQUEST 예외가 발생한다")
  void 접수시각이없으면실패() {
    QrCode qr = saveQr();

    assertThatThrownBy(() -> qrService.markUsed(qr.getToken(), null))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST));
    assertThat(qrCodeRepository.findById(qr.getId()).orElseThrow().getStatus())
        .isEqualTo(QrStatus.WAITING);
  }

  @Test
  @DisplayName("존재하지 않는 토큰이면 QR_NOT_FOUND 예외가 발생한다")
  void 없는토큰은사용처리실패() {
    assertThatThrownBy(() -> qrService.markUsed("unknown-token", LocalDateTime.now(clock)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_NOT_FOUND));
  }

  @Test
  @DisplayName("스캔 조회 시 QR 식별자, 가맹점 정보, 금액, 적립 대상 여부, 만료 시각을 반환한다")
  void 스캔조회성공() {
    Long merchantId = saveMerchant();
    QrCreateResponse created = qrService.createDynamic(merchantId, new QrCreateRequest(5000L));
    QrScanResponse response = qrService.scan(created.token());

    assertThat(response.merchantId()).isEqualTo(merchantId);
    assertThat(response.merchantName()).isEqualTo("QR 테스트 가맹점");
    assertThat(response.amount()).isEqualTo(5000L);
    assertThat(response.cashbackEligible()).isTrue();
    assertThat(response.expiresAt()).isEqualTo(created.expiresAt());
  }

  @Test
  @DisplayName("스캔 조회 시 만료 시각 직전까지는 조회되고 정각부터는 QR_EXPIRED 예외가 발생한다")
  void 스캔조회만료경계() {
    Long merchantId = saveMerchant();
    QrCreateResponse created = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));

    clock.advance(Duration.ofMinutes(2).plusSeconds(59));
    assertThat(qrService.scan(created.token()).amount()).isEqualTo(1000L);

    clock.advance(Duration.ofSeconds(1));
    assertThatThrownBy(() -> qrService.scan(created.token()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_EXPIRED));
  }

  @Test
  @DisplayName("스캔 조회 시 이미 사용된 QR 이면 QR_ALREADY_USED 예외가 발생한다")
  void 스캔조회사용됨() {
    Long merchantId = saveMerchant();
    QrCreateResponse created = qrService.createDynamic(merchantId, new QrCreateRequest(1000L));
    QrCode qrCode = qrCodeRepository.findByToken(created.token()).orElseThrow();
    qrService.markUsed(qrCode.getToken(), LocalDateTime.now(clock));
    entityManager.clear();

    assertThatThrownBy(() -> qrService.scan(created.token()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_ALREADY_USED));
  }

  @Test
  @DisplayName("스캔 조회 시 존재하지 않는 토큰이면 QR_NOT_FOUND 예외가 발생한다")
  void 스캔조회없는토큰() {
    assertThatThrownBy(() -> qrService.scan("unknown-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_NOT_FOUND));
  }

  @Test
  @DisplayName("스캔 조회 시 가맹점이 없으면 MERCHANT_NOT_FOUND 예외가 발생한다")
  void 스캔조회가맹점없음() {
    QrCode qrCode =
        qrCodeRepository.save(QrCode.createDynamic(Long.MAX_VALUE, 1000L, LocalDateTime.now(clock)));

    assertThatThrownBy(() -> qrService.scan(qrCode.getToken()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }

  @Test
  @DisplayName("결제 대상 조회 시 QR 에 저장된 가맹점과 금액을 반환한다")
  void 결제대상조회성공() {
    QrCode qr = saveQr();

    QrPaymentTarget target = qrService.getPaymentTarget(qr.getToken());

    assertThat(target.merchantId()).isEqualTo(qr.getMerchantId());
    assertThat(target.amount()).isEqualTo(qr.getAmount());
  }

  @Test
  @DisplayName("결제 대상 조회는 QR 상태를 검사하지 않고 값만 반환한다")
  void 결제대상조회는상태를검사하지않는다() {
    QrCode used = saveQr();
    qrService.markUsed(used.getToken(), LocalDateTime.now(clock));
    QrCode expired = saveQr();
    clock.advance(Duration.ofMinutes(10));

    assertThat(qrService.getPaymentTarget(used.getToken()).amount()).isEqualTo(used.getAmount());
    assertThat(qrService.getPaymentTarget(expired.getToken()).amount())
        .isEqualTo(expired.getAmount());
  }

  @Test
  @DisplayName("결제 대상 조회 시 존재하지 않는 토큰이면 QR_NOT_FOUND 예외가 발생한다")
  void 결제대상조회없는토큰() {
    assertThatThrownBy(() -> qrService.getPaymentTarget("unknown-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QrErrorCode.QR_NOT_FOUND));
  }

  private QrCode saveQr() {
    // 결제 대상 조회가 가맹점 상태를 확인하므로 가맹점도 함께 저장한다(점주당 하나라 번호를 달리한다)
    Long merchantId =
        merchantRepository
            .save(Merchant.create(nextOwnerId++, "QR 테스트 가맹점", "인천광역시 남동구 1", true))
            .getId();
    return qrCodeRepository.save(QrCode.createDynamic(merchantId, 1000L, LocalDateTime.now(clock)));
  }

  private Long saveMerchant() {
    return merchantRepository
        .save(Merchant.create(1L, "QR 테스트 가맹점", "인천광역시 남동구 1", true))
        .getId();
  }
}
