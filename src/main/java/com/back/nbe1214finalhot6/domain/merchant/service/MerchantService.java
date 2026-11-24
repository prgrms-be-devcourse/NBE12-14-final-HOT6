package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantUpdateRequest;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.MerchantWalletService;
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
  private final MerchantWalletService merchantWalletService;

  /**
   * 점주(ownerId)의 가맹점을 등록한다. 점주 한 명당 하나만 등록할 수 있다.
   *
   * <p>미리 확인해서 MER-002 로 거절하고, 동시에 두 번 눌러 둘 다 확인을 통과해도 DB 유일 제약이 막아서 같은 MER-002 로 응답한다.
   */
  @Transactional
  public MerchantRegisterResponse register(Long ownerId, MerchantRegisterRequest request) {
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
                  request.resolveCashbackEligible()));
    } catch (DataIntegrityViolationException e) {
      if (isOwnerUniqueViolation(e)) {
        throw new BusinessException(MerchantErrorCode.MERCHANT_ALREADY_REGISTERED);
      }
      throw e;
    }
    merchantWalletService.createFor(merchant.getId());

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

  @Transactional(readOnly = true)
  public MerchantResponse get(Long merchantId) {
    return merchantRepository
        .findById(merchantId)
        .map(MerchantResponse::from)
        .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
  }

  @Transactional
  public MerchantResponse update(Long merchantId, MerchantUpdateRequest request) {
    Merchant merchant =
        merchantRepository
            .findById(merchantId)
            .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    merchant.update(request.name(), request.address());

    return MerchantResponse.from(merchant);
  }

  @Transactional(readOnly = true)
  public PageResponse<MerchantResponse> search(String name, int page, int size) {
    Pageable pageable =
        PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by("name", "id"));

    return PageResponse.from(
        merchantRepository
            .findByNameContainingIgnoreCase(name.strip(), pageable)
            .map(MerchantResponse::from));
  }
}
