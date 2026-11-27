package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantUpdateRequest;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.LocationChange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 가맹점 수정. 주소가 바뀌면 좌표도 다시 정한다.
 *
 * <p>외부 호출(주소 변환)이 DB 트랜잭션을 붙잡지 않도록 이 클래스는 트랜잭션을 열지 않고, 읽기와 저장을 각각 {@link MerchantService}의 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class MerchantUpdateFacade {

  private final MerchantService merchantService;
  private final MerchantLocationResolver locationResolver;

  public MerchantResponse update(Long merchantId, MerchantUpdateRequest request) {
    MerchantResponse current = merchantService.get(merchantId); // 없으면 MER-001
    LocationChange change = locationResolver.resolveForUpdate(current, request.address());
    return merchantService.update(merchantId, request, change);
  }
}
