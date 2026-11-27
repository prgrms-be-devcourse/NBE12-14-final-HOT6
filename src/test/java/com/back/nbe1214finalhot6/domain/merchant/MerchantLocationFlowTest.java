package com.back.nbe1214finalhot6.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.GeocodingClient;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.GeocodingResult;
import com.back.nbe1214finalhot6.domain.merchant.geocoding.Location;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주소를 좌표로 바꿔 저장하는 흐름을 실제 보안 설정과 DB 로 확인한다. 네이버는 가짜 구현으로 대신해서 실제 호출하지 않는다.
 *
 * <p>테스트 전체를 한 트랜잭션으로 감싸서(끝나면 롤백) 따로 정리하지 않는다. 가짜 구현이 트랜잭션 밖의 호출이어도 상관없다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MerchantLocationFlowTest {

  private static final Location INCHEON = new Location(37.4563, 126.7052);
  private static final Location SEOUL = new Location(37.5665, 126.9780);

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired UserRepository userRepository;
  @Autowired MerchantRepository merchantRepository;
  @MockitoBean GeocodingClient geocodingClient;

  private long ownerSeq = 1;

  @Test
  @DisplayName("주소로 등록하면 서버가 변환한 좌표가 저장되고 응답과 내 가맹점 조회에 나온다")
  void 등록하면_좌표_저장() throws Exception {
    given(geocodingClient.geocode("인천광역시 남동구 구월로 1")).willReturn(GeocodingResult.found(INCHEON));
    String token = ownerToken();

    register(token, "{\"name\": \"가게\", \"address\": \"인천광역시 남동구 구월로 1\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.latitude").value(37.4563))
        .andExpect(jsonPath("$.data.longitude").value(126.7052));

    mockMvc
        .perform(get("/api/merchants/me").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.data.latitude").value(37.4563))
        .andExpect(jsonPath("$.data.longitude").value(126.7052));
  }

  @Test
  @DisplayName("요청에 좌표를 보내도 무시하고 서버가 변환한 값만 저장한다")
  void 요청의_좌표는_무시() throws Exception {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.found(INCHEON));

    register(
            ownerToken(),
            "{\"name\": \"가게\", \"address\": \"주소\", \"latitude\": 1.5, \"longitude\": 2.5}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.latitude").value(37.4563))
        .andExpect(jsonPath("$.data.longitude").value(126.7052));
  }

  @Test
  @DisplayName("주소를 찾지 못하면 400 MER-006 이고 가맹점이 만들어지지 않는다")
  void 주소를_못_찾으면_등록_거절() throws Exception {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.notFound());
    long before = merchantRepository.count();

    register(ownerToken(), "{\"name\": \"가게\", \"address\": \"없는 주소\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MER-006"));

    assertThat(merchantRepository.count()).isEqualTo(before);
  }

  @Test
  @DisplayName("외부 서비스를 쓸 수 없어도 등록은 되고 좌표만 비어 있다")
  void 외부_실패해도_등록() throws Exception {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.unavailable());

    register(ownerToken(), "{\"name\": \"가게\", \"address\": \"주소\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.latitude").isEmpty())
        .andExpect(jsonPath("$.data.longitude").isEmpty());
  }

  @Test
  @DisplayName("주소를 바꾸면 좌표도 새 주소로 바뀌고, 같은 주소로 수정하면 다시 변환하지 않는다")
  void 주소_수정() throws Exception {
    given(geocodingClient.geocode("서울특별시 중구 세종대로 110")).willReturn(GeocodingResult.found(SEOUL));
    long userId = ownerId();
    String token = token(userId);
    saveMerchant(userId, "가게", "옛 주소", INCHEON);

    patchMe(token, "{\"address\": \"서울특별시 중구 세종대로 110\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.latitude").value(37.5665))
        .andExpect(jsonPath("$.data.longitude").value(126.978));

    // 이름만 고치거나 같은 주소를 다시 보내면 변환하지 않고 좌표가 그대로다
    patchMe(token, "{\"name\": \"새 이름\", \"address\": \"서울특별시 중구 세종대로 110\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.latitude").value(37.5665));
    verify(geocodingClient, times(1)).geocode(anyString());
  }

  @Test
  @DisplayName("바뀐 주소를 찾지 못하면 400 MER-006 이고 가맹점은 그대로다")
  void 바뀐_주소_못찾으면_수정_거절() throws Exception {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.notFound());
    long userId = ownerId();
    saveMerchant(userId, "가게", "옛 주소", INCHEON);

    patchMe(token(userId), "{\"address\": \"오타 주소\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MER-006"));

    Merchant merchant = merchantRepository.findByOwnerId(userId).orElseThrow();
    assertThat(merchant.getAddress()).isEqualTo("옛 주소");
    assertThat(merchant.getLatitude()).isEqualTo(37.4563);
  }

  @Test
  @DisplayName("주소를 바꿨는데 외부 서비스를 쓸 수 없으면 주소는 바뀌고 틀린 옛 좌표는 비워진다")
  void 주소_수정_외부_실패는_좌표_비움() throws Exception {
    given(geocodingClient.geocode(anyString())).willReturn(GeocodingResult.unavailable());
    long userId = ownerId();
    saveMerchant(userId, "가게", "옛 주소", INCHEON);

    patchMe(token(userId), "{\"address\": \"새 주소\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.address").value("새 주소"))
        .andExpect(jsonPath("$.data.latitude").isEmpty());
  }

  @Test
  @DisplayName("좌표가 비어 있는 기존 가맹점은 수정할 때 변환을 시도해 좌표를 채운다")
  void 기존_가맹점_좌표_채움() throws Exception {
    given(geocodingClient.geocode("기존 주소")).willReturn(GeocodingResult.found(INCHEON));
    long userId = ownerId();
    saveMerchant(userId, "가게", "기존 주소", null);

    patchMe(token(userId), "{\"name\": \"새 이름\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.latitude").value(37.4563));
  }

  @Test
  @DisplayName("이름 검색 응답에도 좌표가 담기고, 좌표가 없는 가맹점은 null 로 나온다")
  void 검색_응답에_좌표() throws Exception {
    long a = ownerId();
    long b = ownerId();
    saveMerchant(a, "위치검색유니크 가게 A", "주소 A", INCHEON);
    saveMerchant(b, "위치검색유니크 가게 B", "주소 B", null);
    String userToken = userToken();

    mockMvc
        .perform(
            get("/api/merchants")
                .param("name", "위치검색유니크")
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].name").value("위치검색유니크 가게 A"))
        .andExpect(jsonPath("$.data.content[0].latitude").value(37.4563))
        .andExpect(jsonPath("$.data.content[1].latitude").isEmpty());
  }

  @Test
  @DisplayName("점주가 아니면 등록할 수 없고 변환도 부르지 않는다")
  void 점주가_아니면_변환_호출_없음() throws Exception {
    register(userToken(), "{\"name\": \"가게\", \"address\": \"주소\"}").andExpect(status().isForbidden());

    verify(geocodingClient, never()).geocode(anyString());
  }

  private long ownerId() {
    long n = ownerSeq++;
    return userRepository
        .save(User.create("점주", "owner" + n + "@devpay.com", "0106000" + (1000 + n), "h", "p", UserRole.OWNER))
        .getId();
  }

  private String userToken() {
    long n = ownerSeq++;
    User user =
        userRepository.save(
            User.create("이용자", "user" + n + "@devpay.com", "0107000" + (1000 + n), "h", "p", UserRole.USER));
    return jwtProvider.createAccessToken(user.getId(), UserRole.USER);
  }

  private String ownerToken() {
    return token(ownerId());
  }

  private String token(long userId) {
    return jwtProvider.createAccessToken(userId, UserRole.OWNER);
  }

  private void saveMerchant(long ownerId, String name, String address, Location location) {
    merchantRepository.save(Merchant.create(ownerId, name, address, true, location));
  }

  private ResultActions register(String token, String body) throws Exception {
    return mockMvc.perform(
        post("/api/merchants")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions patchMe(String token, String body) throws Exception {
    return mockMvc.perform(
        patch("/api/merchants/me")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }
}
