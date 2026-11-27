package com.back.nbe1214finalhot6.domain.merchant.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 네이버 지도(Geocoding) 설정. 값은 환경 변수(.env)로만 받고, 키가 비어 있으면 주소 변환을 건너뛴다.
 *
 * @param clientId 네이버 클라우드 Application 의 Client ID
 * @param clientSecret Client Secret. 서버에서만 쓰고 로그와 응답에 남기지 않는다
 * @param geocodeUrl 주소를 좌표로 바꾸는 요청 주소
 * @param connectTimeoutMs 연결 제한 시간(밀리초)
 * @param readTimeoutMs 응답 제한 시간(밀리초)
 */
@ConfigurationProperties(prefix = "naver.map")
public record NaverMapProperties(
    @DefaultValue("") String clientId,
    @DefaultValue("") String clientSecret,
    @DefaultValue("https://maps.apigw.ntruss.com/map-geocode/v2/geocode") String geocodeUrl,
    @DefaultValue("2000") int connectTimeoutMs,
    @DefaultValue("3000") int readTimeoutMs) {

  /** 두 키가 모두 채워져 있어 변환을 호출할 수 있는지. */
  public boolean configured() {
    return !clientId.isBlank() && !clientSecret.isBlank();
  }

  // Secret 이 로그에 찍히지 않게 가린다
  @Override
  public String toString() {
    return "NaverMapProperties[clientId=" + clientId + ", clientSecret=****, geocodeUrl=" + geocodeUrl + "]";
  }
}
