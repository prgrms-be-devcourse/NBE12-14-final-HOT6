package com.back.nbe1214finalhot6.domain.merchant.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.back.nbe1214finalhot6.domain.merchant.geocoding.GeocodingResult.Status;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 네이버 Geocoding 호출을 가짜 서버로 대신해서, 실제 네이버를 호출하지 않고 응답 해석과 실패 처리를 확인한다. */
class NaverGeocodingClientTest {

  private static final String URL = "https://maps.test/map-geocode/v2/geocode";
  private static final NaverMapProperties CONFIGURED =
      new NaverMapProperties("test-id", "test-secret", URL, 2000, 3000);

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

  private NaverGeocodingClient client(NaverMapProperties properties) {
    return new NaverGeocodingClient(properties, builder.build());
  }

  @Test
  @DisplayName("정상 응답이면 y 를 위도, x 를 경도로 읽고 키 헤더를 보낸다")
  void 정상_응답_좌표_순서() {
    server
        .expect(method(HttpMethod.GET))
        .andExpect(
            request ->
                assertThat(
                        URLDecoder.decode(
                            request.getURI().getRawQuery().replace("query=", ""), StandardCharsets.UTF_8))
                    .isEqualTo("인천광역시 남동구 구월로 1"))
        .andExpect(header("X-NCP-APIGW-API-KEY-ID", "test-id"))
        .andExpect(header("X-NCP-APIGW-API-KEY", "test-secret"))
        .andRespond(
            withSuccess(
                "{\"status\":\"OK\",\"addresses\":[{\"roadAddress\":\"도로명\",\"x\":\"126.7052\",\"y\":\"37.4563\"}]}",
                MediaType.APPLICATION_JSON));

    GeocodingResult result = client(CONFIGURED).geocode("인천광역시 남동구 구월로 1");

    assertThat(result.status()).isEqualTo(Status.FOUND);
    assertThat(result.location().latitude()).isEqualTo(37.4563);
    assertThat(result.location().longitude()).isEqualTo(126.7052);
    server.verify();
  }

  @Test
  @DisplayName("주소가 여럿이면 첫 번째를 쓴다")
  void 여러_결과는_첫번째() {
    server
        .expect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"status\":\"OK\",\"addresses\":[{\"x\":\"126.1\",\"y\":\"37.1\"},{\"x\":\"127.2\",\"y\":\"38.2\"}]}",
                MediaType.APPLICATION_JSON));

    GeocodingResult result = client(CONFIGURED).geocode("주소");

    assertThat(result.location().latitude()).isEqualTo(37.1);
  }

  @Test
  @DisplayName("결과가 하나도 없으면 NOT_FOUND 다")
  void 결과_없음() {
    server
        .expect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":\"OK\",\"addresses\":[]}", MediaType.APPLICATION_JSON));

    assertThat(client(CONFIGURED).geocode("없는 주소").status()).isEqualTo(Status.NOT_FOUND);
  }

  @Test
  @DisplayName("서버 오류나 키 오류(4xx, 5xx)는 예외 없이 UNAVAILABLE 이다")
  void 서버_오류() {
    server.expect(method(HttpMethod.GET)).andRespond(withServerError());

    assertThat(client(CONFIGURED).geocode("주소").status()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  @DisplayName("응답 상태가 OK 가 아니면 UNAVAILABLE 이다")
  void 상태가_정상이_아님() {
    server
        .expect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":\"INVALID_REQUEST\",\"addresses\":[]}", MediaType.APPLICATION_JSON));

    assertThat(client(CONFIGURED).geocode("주소").status()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  @DisplayName("좌표가 숫자가 아니거나 범위를 벗어나면 UNAVAILABLE 이다")
  void 잘못된_좌표() {
    server
        .expect(method(HttpMethod.GET))
        .andRespond(
            withSuccess("{\"status\":\"OK\",\"addresses\":[{\"x\":\"abc\",\"y\":\"37.1\"}]}", MediaType.APPLICATION_JSON));
    assertThat(client(CONFIGURED).geocode("주소").status()).isEqualTo(Status.UNAVAILABLE);

    server.reset();
    server
        .expect(method(HttpMethod.GET))
        .andRespond(
            withSuccess("{\"status\":\"OK\",\"addresses\":[{\"x\":\"126.1\",\"y\":\"137.1\"}]}", MediaType.APPLICATION_JSON));
    assertThat(client(CONFIGURED).geocode("주소").status()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  @DisplayName("키가 비어 있으면 호출하지 않고 UNAVAILABLE 이다")
  void 키가_없으면_호출하지_않음() {
    NaverMapProperties empty = new NaverMapProperties("", "", URL, 2000, 3000);

    GeocodingResult result = client(empty).geocode("주소");

    assertThat(result.status()).isEqualTo(Status.UNAVAILABLE);
    server.verify(); // 기대한 요청이 없으므로, 요청이 나갔다면 위에서 실패한다
  }

  @Test
  @DisplayName("설정을 문자열로 찍어도 Secret 은 가려진다")
  void secret_은_로그에_남지_않는다() {
    assertThat(CONFIGURED.toString()).doesNotContain("test-secret").contains("****");
  }

  @Test
  @DisplayName("범위를 벗어난 위도·경도로는 위치를 만들 수 없다")
  void 위치_범위_검증() {
    assertThatThrownBy(() -> new Location(91, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Location(0, 181)).isInstanceOf(IllegalArgumentException.class);
    assertThat(new Location(-90, -180).latitude()).isEqualTo(-90);
  }
}
