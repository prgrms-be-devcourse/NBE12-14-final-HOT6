package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.auth.service.UserRoleService;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가맹점 등록. 활동 중인 점주 계정만 등록할 수 있다. 계정의 역할은 바꾸지 않는다. */
@Service
@RequiredArgsConstructor
public class MerchantRegistrationFacade {

  private final MerchantService merchantService;
  private final UserRoleService userRoleService;

  @Transactional
  public MerchantRegisterResponse register(AuthUser user, MerchantRegisterRequest request) {
    userRoleService.requireOwner(user.userId()); // 점주가 아니면 가맹점을 만들기 전에 거절한다
    return merchantService.register(user.userId(), request);
  }
}
