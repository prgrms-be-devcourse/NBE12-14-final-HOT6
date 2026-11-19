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

  @Transactional
  public MerchantRegisterResponse register(MerchantRegisterRequest request) {
    Merchant merchant =
        merchantRepository.save(
            Merchant.create(
                request.ownerId(),
                request.name(),
                request.address(),
                request.resolveCashbackEligible()));

    return MerchantRegisterResponse.from(merchant);
  }

  @Transactional
  public MerchantResponse update(Long merchantId, MerchantUpdateRequest request) {
    Merchant merchant =
        merchantRepository
            .findById(merchantId)
            .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    // TODO: 인증(COM-03) 머지 후 로그인한 사장님의 ID 와 merchant.getOwnerId() 를 비교해 본인 가맹점만 수정하도록 검사한다.
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
