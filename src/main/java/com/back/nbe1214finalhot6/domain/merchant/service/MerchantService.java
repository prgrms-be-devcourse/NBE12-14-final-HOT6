package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantService {

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

  // 결제 도메인이 적립 대상 가맹점인지 확인할 때 사용 (엔티티를 내보내지 않음)
  @Transactional(readOnly = true)
  public boolean isCashbackEligible(Long merchantId) {
    return merchantRepository
        .findById(merchantId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND))
        .isCashbackEligible();
  }
}
