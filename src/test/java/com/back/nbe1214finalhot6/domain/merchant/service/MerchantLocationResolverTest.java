package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.GeocodingClient;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.GeocodingResult;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.Location;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.LocationChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantLocationResolverTest {

  private static final Location FOUND = new Location(37.4563, 126.7052);

  @Mock GeocodingClient geocodingClient;
  @InjectMocks MerchantLocationResolver resolver;

  private MerchantResponse current(String address, Double lat, Double lng) {
    return new MerchantResponse(1L, "가게", address, true, MerchantStatus.ACTIVE, lat, lng);
  }

  @Test
  @DisplayName("등록: 주소를 찾으면 그 좌표를 돌려준다")
  void 등록_찾음() {
    given(geocodingClient.geocode("인천광역시 남동구 1")).willReturn(GeocodingResult.found(FOUND));

    assertThat(resolver.resolveForRegister(" 인천광역시 남동구 1 ")).isEqualTo(FOUND);
  }

  @Test
  @DisplayName("등록: 주소를 찾지 못하면 MER-006 으로 거절한다")
  void 등록_못찾음() {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.notFound());

    assertThatThrownBy(() -> resolver.resolveForRegister("없는 주소"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_ADDRESS_NOT_FOUND));
  }

  @Test
  @DisplayName("등록: 외부 서비스를 쓸 수 없으면 막지 않고 위치 없이(null) 진행한다")
  void 등록_외부_실패() {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.unavailable());

    assertThat(resolver.resolveForRegister("주소")).isNull();
  }

  @Test
  @DisplayName("수정: 주소가 그대로이고 좌표가 있으면 변환을 부르지 않고 유지한다")
  void 수정_변화_없음() {
    LocationChange change = resolver.resolveForUpdate(current("주소", 37.1, 126.1), "주소");

    assertThat(change.apply()).isFalse();
    verify(geocodingClient, never()).geocode(anyString());
  }

  @Test
  @DisplayName("수정: 주소를 보내지 않았고 좌표가 있으면 변환을 부르지 않는다")
  void 수정_주소_미전달() {
    assertThat(resolver.resolveForUpdate(current("주소", 37.1, 126.1), null).apply()).isFalse();
    verify(geocodingClient, never()).geocode(anyString());
  }

  @Test
  @DisplayName("수정: 주소가 바뀌면 새 주소를 변환해 새 좌표로 바꾼다")
  void 수정_주소_바뀜() {
    given(geocodingClient.geocode("새 주소")).willReturn(GeocodingResult.found(FOUND));

    LocationChange change = resolver.resolveForUpdate(current("옛 주소", 37.1, 126.1), "새 주소");

    assertThat(change.apply()).isTrue();
    assertThat(change.location()).isEqualTo(FOUND);
  }

  @Test
  @DisplayName("수정: 바뀐 주소를 찾지 못하면 MER-006 으로 거절한다")
  void 수정_바뀐_주소_못찾음() {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.notFound());

    assertThatThrownBy(() -> resolver.resolveForUpdate(current("옛 주소", 37.1, 126.1), "오타 주소"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_ADDRESS_NOT_FOUND));
  }

  @Test
  @DisplayName("수정: 주소가 바뀌었는데 외부 서비스를 쓸 수 없으면 옛 좌표는 틀린 값이라 비운다")
  void 수정_바뀐_주소_외부_실패는_비움() {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.unavailable());

    LocationChange change = resolver.resolveForUpdate(current("옛 주소", 37.1, 126.1), "새 주소");

    assertThat(change.apply()).isTrue();
    assertThat(change.location()).isNull();
  }

  @Test
  @DisplayName("수정: 좌표가 비어 있으면 주소가 같아도 변환을 시도해 채운다")
  void 수정_좌표_없으면_채움() {
    given(geocodingClient.geocode("주소")).willReturn(GeocodingResult.found(FOUND));

    LocationChange change = resolver.resolveForUpdate(current("주소", null, null), "주소");

    assertThat(change.location()).isEqualTo(FOUND);
  }

  @Test
  @DisplayName("수정: 주소를 보내지 않아도 좌표가 비어 있으면 현재 주소로 변환한다")
  void 수정_주소_미전달_좌표_없음() {
    given(geocodingClient.geocode("현재 주소")).willReturn(GeocodingResult.found(FOUND));

    LocationChange change = resolver.resolveForUpdate(current("현재 주소", null, null), null);

    assertThat(change.location()).isEqualTo(FOUND);
  }

  @Test
  @DisplayName("수정: 좌표 채우기가 실패해도(못 찾음, 외부 실패) 이름만 고치는 수정은 막지 않고 그대로 둔다")
  void 수정_좌표_채우기_실패는_막지_않음() {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.notFound());
    assertThat(resolver.resolveForUpdate(current("오래된 주소", null, null), "오래된 주소").apply()).isFalse();

    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.unavailable());
    assertThat(resolver.resolveForUpdate(current("오래된 주소", null, null), null).apply()).isFalse();
  }
}
