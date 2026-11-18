package com.back.nbe1214finalhot6.domain.merchant.service;

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
}
