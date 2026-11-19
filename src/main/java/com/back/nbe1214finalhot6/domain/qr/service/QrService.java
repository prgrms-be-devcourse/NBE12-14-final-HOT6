package com.back.nbe1214finalhot6.domain.qr.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateResponse;
import com.back.nbe1214finalhot6.domain.qr.dto.QrStatusResponse;
import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import com.back.nbe1214finalhot6.domain.qr.exception.QrErrorCode;
import com.back.nbe1214finalhot6.domain.qr.repository.QrCodeRepository;
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
}
