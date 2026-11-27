package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantUpdateRequest;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.Location;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.LocationChange;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantService {

  private static final int MAX_PAGE_SIZE = 50;

  private final MerchantRepository merchantRepository;

  /**
   * 점주(ownerId)의 가맹점을 등록한다. 점주 한 명당 하나만 등록할 수 있다.
   *
   * <p>미리 확인해서 MER-002 로 거절하고, 동시에 두 번 눌러 둘 다 확인을 통과해도 DB 유일 제약이 막아서 같은 MER-002 로 응답한다.
   *
   * @param location 주소를 변환한 좌표. 알 수 없으면 null
   */
  @Transactional
  public MerchantRegisterResponse register(
      Long ownerId, MerchantRegisterRequest request, Location location) {
    if (merchantRepository.existsByOwnerId(ownerId)) {
      throw new BusinessException(MerchantErrorCode.MERCHANT_ALREADY_REGISTERED);
    }

    Merchant merchant;
    try {
      // 제약 위반을 이 자리에서 잡으려고 바로 반영한다
      merchant =
          merchantRepository.saveAndFlush(
              Merchant.create(
                  ownerId,
                  request.name(),
                  request.address(),
                  request.resolveCashbackEligible(),
                  location));
    } catch (DataIntegrityViolationException e) {
      if (isOwnerUniqueViolation(e)) {
        throw new BusinessException(MerchantErrorCode.MERCHANT_ALREADY_REGISTERED);
      }
      throw e;
    }

    return MerchantRegisterResponse.from(merchant);
  }

  // 점주당 하나 제약을 어긴 것인지(다른 제약 위반은 그대로 던진다). 에러 메시지 글자가 아니라 DB 가 알려 준 제약 이름으로 판단한다
  private boolean isOwnerUniqueViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException violation
          && Merchant.OWNER_UNIQUE.equalsIgnoreCase(violation.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  // 결제 도메인이 적립 대상 가맹점인지 확인할 때 사용 (엔티티를 내보내지 않음)
  @Transactional(readOnly = true)
  public boolean isCashbackEligible(Long merchantId) {
    return merchantRepository
        .findById(merchantId)
        .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND))
        .isCashbackEligible();
  }

  /**
   * 영업 중인 가맹점인지 확인한다. 해지 신청 뒤(CLOSING)와 해지 뒤(CLOSED)에는 결제와 QR 을 막으려고 다른 도메인이 부른다.
   *
   * @throws BusinessException 없으면 MER-001, 해지 진행 중이면 MER-003, 해지됐으면 MER-004
   */
  @Transactional(readOnly = true)
  public void assertActive(Long merchantId) {
    MerchantStatus status =
        merchantRepository
            .findStatusById(merchantId)
            .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    MerchantStatusGuard.assertActive(status);
  }

  /** 점주의 가맹점 상태. 가맹점이 없으면 비어 있다. 점주 탈퇴 가능 여부를 정하는 쪽(회원)이 쓴다. */
  @Transactional(readOnly = true)
  public Optional<MerchantStatus> findStatusByOwner(Long ownerId) {
    return merchantRepository.findStatusByOwnerId(ownerId);
  }

  @Transactional(readOnly = true)
  public MerchantResponse get(Long merchantId) {
    return merchantRepository
        .findById(merchantId)
        .map(MerchantResponse::from)
        .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }

  /** 이름·주소와 좌표를 고친다. 좌표는 {@code locationChange} 가 정한다(유지, 새 값, 비우기). */
  @Transactional
  public MerchantResponse update(
      Long merchantId, MerchantUpdateRequest request, LocationChange locationChange) {
    Merchant merchant =
        merchantRepository
            .findById(merchantId)
            .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    merchant.update(request.name(), request.address());
    if (locationChange.apply()) {
      merchant.updateLocation(locationChange.location());
    }

    return MerchantResponse.from(merchant);
  }

  @Transactional(readOnly = true)
  public PageResponse<MerchantResponse> search(String name, int page, int size) {
    Pageable pageable =
        PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("name", "id"));

    return PageResponse.from(
        merchantRepository
            .findByStatusAndNameContainingIgnoreCase(MerchantStatus.ACTIVE, name.strip(), pageable)
            .map(MerchantResponse::from));
  }
}
