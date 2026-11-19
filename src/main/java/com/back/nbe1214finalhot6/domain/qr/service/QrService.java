package com.back.nbe1214finalhot6.domain.qr.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrScanResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrStatusResponse;
import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;
import com.back.nbe1214finalhot6.domain.qr.exception.QrErrorCode;
import com.back.nbe1214finalhot6.domain.qr.repository.QrCodeRepository;
import com.back.nbe1214finalhot6.domain.qr.repository.QrSnapshot;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QrService {

  private final QrCodeRepository qrCodeRepository;
  private final MerchantRepository merchantRepository;
  private final Clock clock;

  @Transactional
  public QrCreateResponse createDynamic(Long merchantId, QrCreateRequest request) {
    if (!merchantRepository.existsById(merchantId)) {
      throw new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND);
    }

    // TODO: 인증(COM-03) 머지 후 로그인한 사장님의 ID 와 가맹점의 ownerId 를 비교해 본인 가맹점에서만 생성하도록 검사한다.
    QrCode qrCode =
        qrCodeRepository.save(
            QrCode.createDynamic(merchantId, request.amount(), LocalDateTime.now(clock)));

    return QrCreateResponse.from(qrCode);
  }

  @Transactional(readOnly = true)
  public QrStatusResponse getStatus(String token) {
    QrCode qrCode =
        qrCodeRepository
            .findByToken(token)
            .orElseThrow(() -> new BusinessException(QrErrorCode.QR_NOT_FOUND));

    return QrStatusResponse.of(qrCode, LocalDateTime.now(clock));
  }

  @Transactional(readOnly = true)
  public QrScanResponse scan(String token) {
    QrCode qrCode =
        qrCodeRepository
            .findByToken(token)
            .orElseThrow(() -> new BusinessException(QrErrorCode.QR_NOT_FOUND));

    QrStatus status = qrCode.statusAt(LocalDateTime.now(clock));
    if (status != QrStatus.WAITING) {
      throw unusable(status);
    }

    Merchant merchant =
        merchantRepository
            .findById(qrCode.getMerchantId())
            .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    return QrScanResponse.of(qrCode, merchant);
  }

  @Transactional
  public void markUsed(String qrToken, Long merchantId, long amount, LocalDateTime requestedAt) {
    if (requestedAt == null) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    int updated =
        qrCodeRepository.markUsedIfMatches(
            qrToken, merchantId, amount, requestedAt, LocalDateTime.now(clock));
    if (updated == 1) {
      return;
    }

    // 호출한 트랜잭션의 영속성 컨텍스트에 남은 이전 값을 읽지 않도록 엔티티 대신 값만 조회한다.
    QrSnapshot snapshot =
        qrCodeRepository
            .findSnapshotByToken(qrToken)
            .orElseThrow(() -> new BusinessException(QrErrorCode.QR_NOT_FOUND));
    if (!snapshot.merchantId().equals(merchantId) || !snapshot.amount().equals(amount)) {
      throw new BusinessException(QrErrorCode.QR_MISMATCH);
    }
    throw unusable(snapshot.status());
  }

  // 사용할 수 없는 QR 의 사유. 사용됨이 아니면 만료로 본다. (DB 의 WAITING 이 여기 도달했다면 접수 시각 기준으로 만료된 경우다)
  private BusinessException unusable(QrStatus status) {
    return new BusinessException(
        status == QrStatus.USED ? QrErrorCode.QR_ALREADY_USED : QrErrorCode.QR_EXPIRED);
  }
}
