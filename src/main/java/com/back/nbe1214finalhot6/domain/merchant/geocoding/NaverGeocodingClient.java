package com.back.nbe1214finalhot6.domain.merchant.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 네이버 클라우드 Maps Geocoding 으로 주소를 좌표로 바꾼다.
 *
 * <p>키가 비어 있으면 호출하지 않고 {@code UNAVAILABLE} 이다. 호출이 실패해도 예외를 던지지 않으며, 로그에는 상태만 남기고 키는 남기지 않는다. 같은 주소가 여럿
 * 나오면 첫 번째를 쓴다(점주가 주소 찾기에서 고른 표준 주소라 거의 하나로 정해진다).
 */
@Slf4j
@Component
@EnableConfigurationProperties(NaverMapProperties.class)
public class NaverGeocodingClient implements GeocodingClient {

  private static final String KEY_ID_HEADER = "X-NCP-APIGW-API-KEY-ID";
  private static final String KEY_HEADER = "X-NCP-APIGW-API-KEY";

  private final NaverMapProperties properties;
  private final RestClient restClient;

  @Autowired
  public NaverGeocodingClient(NaverMapProperties properties) {
    this(properties, defaultRestClient(properties));
  }

  // 테스트가 가짜 서버를 붙인 RestClient 를 넣을 수 있게 한다
  NaverGeocodingClient(NaverMapProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  private static RestClient defaultRestClient(NaverMapProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
    return RestClient.builder().requestFactory(requestFactory).build();
  }

  @Override
  public GeocodingResult geocode(String address) {
    if (!properties.configured()) {
      return GeocodingResult.unavailable();
    }
    try {
      NaverResponse response =
          restClient
              .get()
              .uri(properties.geocodeUrl() + "?query={query}", address)
              .header(KEY_ID_HEADER, properties.clientId())
              .header(KEY_HEADER, properties.clientSecret())
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(NaverResponse.class);
      return toResult(response);
    } catch (RuntimeException e) {
      // 시간 초과, 연결 실패, 4xx·5xx(키 오류 포함), 응답 해석 실패. 키 값은 남기지 않는다
      log.warn("주소 변환 호출 실패: {}", e.getClass().getSimpleName());
      return GeocodingResult.unavailable();
    }
  }

  private GeocodingResult toResult(NaverResponse response) {
    if (response == null || !"OK".equals(response.status())) {
      log.warn("주소 변환 응답 상태가 정상이 아님: {}", response == null ? null : response.status());
      return GeocodingResult.unavailable();
    }
    if (response.addresses() == null || response.addresses().isEmpty()) {
      return GeocodingResult.notFound();
    }
    Address first = response.addresses().getFirst();
    try {
      // x 가 경도, y 가 위도다
      return GeocodingResult.found(
          new Location(Double.parseDouble(first.y()), Double.parseDouble(first.x())));
    } catch (IllegalArgumentException e) {
      log.warn("주소 변환 응답의 좌표를 해석하지 못함");
      return GeocodingResult.unavailable();
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record NaverResponse(String status, List<Address> addresses) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Address(String x, String y) {}
}
