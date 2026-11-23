package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.auth.service.UserRoleService;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가맹점 등록과 점주 승격을 한 트랜잭션으로 묶는다.
 *
 * <p>가맹점을 등록한 회원은 승인 절차 없이 점주(OWNER)가 된다. 하나라도 실패하면 둘 다 취소된다. 승격 직후 이미 받은 토큰은 옛 역할(USER)이므로, 점주 기능은 새
 * 토큰(재로그인 또는 재발급)을 받은 뒤에 쓸 수 있다.
 */
@Service
@RequiredArgsConstructor
public class MerchantRegistrationFacade {

  private final MerchantService merchantService;
  private final UserRoleService userRoleService;

  @Transactional
  public MerchantRegisterResponse register(AuthUser user, MerchantRegisterRequest request) {
    userRoleService.promoteToOwner(user.userId()); // 승격할 수 없는 회원이면 가맹점을 만들기 전에 거절한다
    return merchantService.register(user.userId(), request);
  }
}
