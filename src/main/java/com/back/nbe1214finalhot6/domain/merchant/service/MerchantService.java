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

  /** 점주(ownerId)의 가맹점을 등록한다. 등록하는 회원은 요청이 아니라 로그인한 회원(토큰)이다. */
  @Transactional
  public MerchantRegisterResponse register(Long ownerId, MerchantRegisterRequest request) {
    Merchant merchant =
        merchantRepository.save(
            Merchant.create(
                ownerId,
                request.name(),
                request.address(),
                request.resolveCashbackEligible()));
    merchantWalletService.createFor(merchant.getId());

    return MerchantRegisterResponse.from(merchant);
  }

  // 결제 도메인이 적립 대상 가맹점인지 확인할 때 사용 (엔티티를 내보내지 않음)
  @Transactional(readOnly = true)
  public boolean isCashbackEligible(Long merchantId) {
    return merchantRepository
        .findById(merchantId)
        .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND))
        .isCashbackEligible();
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
