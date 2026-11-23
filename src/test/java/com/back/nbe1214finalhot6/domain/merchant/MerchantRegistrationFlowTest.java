package com.back.nbe1214finalhot6.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 → 로그인 → 가맹점 등록 → 점주 기능까지, 실제 보안 설정과 DB 로 끝까지 확인한다.
 *
 * <p>테스트 전체를 한 트랜잭션으로 감싸서(끝나면 롤백) 따로 정리하지 않는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MerchantRegistrationFlowTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;

  @Test
  @DisplayName("가맹점을 등록하면 점주가 되지만, 이미 받은 토큰은 옛 역할이라 다시 로그인해야 점주 기능을 쓴다")
  void registerThenReloginToUseOwnerFeatures() throws Exception {
    signup();
    String userToken = login();
    assertThat(jwtProvider.parse(userToken).role()).isEqualTo(UserRole.USER);

    // 사용자도 가맹점을 등록할 수 있다
    MvcResult registered =
        mockMvc
            .perform(
                post("/api/merchants")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"흐름 가맹점\", \"address\": \"인천광역시 남동구 1\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    int merchantId = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.merchantId");

    // 등록 직후에도 이미 받은 토큰은 옛 역할(USER)이라 점주 기능은 403 이다
    mockMvc
        .perform(patchMe(userToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("COM-008"));

    // 다시 로그인하면 점주(OWNER) 토큰을 받고 점주 기능을 쓸 수 있다
    String ownerToken = login();
    assertThat(jwtProvider.parse(ownerToken).role()).isEqualTo(UserRole.OWNER);
    mockMvc.perform(patchMe(ownerToken)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("요청에 다른 회원의 ownerId 를 보내도 무시하고, 로그인한 회원의 가맹점으로 등록된다")
  void ownerIdInRequestIsIgnored() throws Exception {
    signup();
    String token = login();
    long userId = jwtProvider.parse(token).userId();

    MvcResult registered =
        mockMvc
            .perform(
                post("/api/merchants")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"ownerId\": %d, \"name\": \"가맹점\", \"address\": \"인천광역시 남동구 1\"}"
                            .formatted(userId + 1)))
            .andExpect(status().isCreated())
            .andReturn();
    int merchantId = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.merchantId");

    // 로그인한 회원이 점주가 되어, 새 토큰으로 자기 가맹점을 수정할 수 있다
    mockMvc.perform(patchMe(login())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("이미 가맹점을 등록한 회원이 다시 등록하면 409 MER-002 이다")
  void secondRegistrationIsConflict() throws Exception {
    signup();
    String token = login();
    String body = "{\"name\": \"가맹점\", \"address\": \"인천광역시 남동구 1\"}";

    mockMvc
        .perform(
            post("/api/merchants")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/merchants")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MER-002"));
  }

  @Test
  @DisplayName("로그인하지 않고 가맹점을 등록하면 401 이다")
  void anonymousCannotRegister() throws Exception {
    mockMvc
        .perform(
            post("/api/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"가맹점\", \"address\": \"인천광역시 남동구 1\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("COM-005"));
  }

  private MockHttpServletRequestBuilder patchMe(String token) {
    return patchMe(token, "{\"name\": \"새 이름\"}");
  }

  private MockHttpServletRequestBuilder patchMe(String token, String body) {
    return patch("/api/merchants/me")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private void signup() throws Exception {
    signup("flow@devpay.com", "01099990001");
  }

  private void signup(String email, String phone) throws Exception {
    mockMvc
        .perform(
            post("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "홍길동", "email": "%s", "password": "Password1!",
                     "phone": "%s", "pin": "123456"}
                    """
                        .formatted(email, phone)))
        .andExpect(status().isCreated());
  }

  private String login() throws Exception {
    return login("flow@devpay.com");
  }

  private String login(String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"%s\", \"password\": \"Password1!\"}".formatted(email)))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
  }
}
