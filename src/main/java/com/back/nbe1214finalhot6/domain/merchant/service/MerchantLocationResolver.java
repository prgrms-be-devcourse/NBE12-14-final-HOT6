package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.GeocodingClient;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.GeocodingResult;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.Location;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.LocationChange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가맹점 주소로 좌표를 정한다. 외부 호출이 들어 있으므로 DB 트랜잭션 밖에서 부른다.
 *
 * <p>주소를 찾지 못하면(NOT_FOUND) 등록·수정을 거절한다. 외부 서비스가 실패했거나 설정이 비어 있으면(UNAVAILABLE) 등록·수정을 막지 않고 위치 없이 진행한다.
 */
@Component
@RequiredArgsConstructor
public class MerchantLocationResolver {

  private final GeocodingClient geocodingClient;

  /**
   * 등록할 주소의 좌표. 위치를 알 수 없으면 null 이다.
   *
   * @throws BusinessException 주소를 찾지 못하면 MER-006
   */
  public Location resolveForRegister(String address) {
    GeocodingResult result = geocodingClient.geocode(address.strip());
    return switch (result.status()) {
      case FOUND -> result.location();
      case NOT_FOUND -> throw new BusinessException(MerchantErrorCode.MERCHANT_ADDRESS_NOT_FOUND);
      case UNAVAILABLE -> null;
    };
  }

  /**
   * 수정에서 좌표를 어떻게 할지 정한다.
   *
   * <p>주소가 바뀌었거나 좌표가 비어 있을 때만 변환한다. 주소를 보내지 않았거나 바뀌지 않았는데 옛 주소를 못 찾는 경우는 수정을 막지 않고 그대로 둔다(예: 이름만
   * 고치는데 오래된 주소를 못 찾는다고 거절하지 않는다). 주소가 바뀌었는데 변환이 실패하면 옛 좌표는 틀린 값이라 비운다.
   *
   * @throws BusinessException 바뀐 주소를 찾지 못하면 MER-006
   */
  public LocationChange resolveForUpdate(MerchantResponse current, String requestedAddress) {
    boolean addressGiven = requestedAddress != null;
    boolean addressChanged =
        addressGiven && !requestedAddress.strip().equals(current.address().strip());
    boolean locationMissing = current.latitude() == null || current.longitude() == null;
    if (!addressChanged && !locationMissing) {
      return LocationChange.keep();
    }

    String target = (addressGiven ? requestedAddress : current.address()).strip();
    GeocodingResult result = geocodingClient.geocode(target);
    return switch (result.status()) {
      case FOUND -> LocationChange.set(result.location());
      case NOT_FOUND -> {
        if (addressChanged) {
          throw new BusinessException(MerchantErrorCode.MERCHANT_ADDRESS_NOT_FOUND);
        }
        yield LocationChange.keep();
      }
      case UNAVAILABLE -> addressChanged ? LocationChange.clear() : LocationChange.keep();
    };
  }
}
