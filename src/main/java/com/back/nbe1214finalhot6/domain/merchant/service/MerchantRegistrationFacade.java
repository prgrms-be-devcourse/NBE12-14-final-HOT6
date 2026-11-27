package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.auth.service.UserRoleService;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.Location;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 가맹점 등록. 활동 중인 점주 계정만 등록할 수 있다. 계정의 역할은 바꾸지 않는다.
 *
 * <p>주소를 좌표로 바꾸는 외부 호출이 DB 트랜잭션을 붙잡지 않도록 이 클래스는 트랜잭션을 열지 않는다. 저장은 {@link MerchantService#register} 가 자기 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class MerchantRegistrationFacade {

  private final MerchantService merchantService;
  private final UserRoleService userRoleService;
  private final MerchantLocationResolver locationResolver;

  public MerchantRegisterResponse register(AuthUser user, MerchantRegisterRequest request) {
    userRoleService.requireOwner(user.userId()); // 점주가 아니면 가맹점을 만들기 전에 거절한다
    merchantService.assertNotRegistered(user.userId()); // 이미 있으면 외부 호출 없이 MER-002
    Location location = locationResolver.resolveForRegister(request.address()); // 주소를 못 찾으면 MER-006
    return merchantService.register(user.userId(), request, location);
  }
}
