package com.back.nbe1214finalhot6.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.domain.merchant.controller.MerchantController;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이(로그인·CSRF 토큰 없이) 호출해서 임시 보안 설정도 함께 확인한다.
@WebMvcTest(MerchantController.class)
@Import(SecurityConfig.class)
class MerchantControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private MerchantService merchantService;

  // 목적: 정상 요청이 201 과 공통 응답 형식(success, data)으로 내려가는지 확인한다.
  @Test
  @DisplayName("정상 요청이면 201 과 등록 결과를 반환한다")
  void register_created() throws Exception {
    given(merchantService.register(any()))
        .willReturn(new MerchantRegisterResponse(1L, "테스트 가맹점", "인천광역시 남동구 1", true));

    mockMvc
        .perform(
            post("/api/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ownerId": 1, "name": "테스트 가맹점", "address": "인천광역시 남동구 1"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.merchantId").value(1))
        .andExpect(jsonPath("$.data.cashbackEligible").value(true));
  }

  // 목적: JSON 의 cashbackEligible: false 가 Boolean 으로 바인딩되어 서비스까지 전달되는지 확인한다.
  @Test
  @DisplayName("cashbackEligible 을 false 로 보내면 서비스에 false 로 전달된다")
  void register_cashbackEligibleFalseBinding() throws Exception {
    given(merchantService.register(any()))
        .willReturn(new MerchantRegisterResponse(1L, "비적립 가맹점", "인천광역시 남동구 1", false));

    mockMvc
        .perform(
            post("/api/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ownerId": 1, "name": "비적립 가맹점", "address": "인천광역시 남동구 1",
                     "cashbackEligible": false}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.cashbackEligible").value(false));

    ArgumentCaptor<MerchantRegisterRequest> captor =
        ArgumentCaptor.forClass(MerchantRegisterRequest.class);
    verify(merchantService).register(captor.capture());
    assertThat(captor.getValue().cashbackEligible()).isFalse();
    assertThat(captor.getValue().resolveCashbackEligible()).isFalse();
  }

  // 목적: 이름이 비어 있거나 공백뿐이면 @NotBlank 로 걸러져 400 이 되는지 확인한다.
  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("이름이 비어 있으면 400")
  void register_blankName(String name) throws Exception {
    expectBadRequest(requestBody(name, "인천광역시 남동구 1"), "name");
  }

  // 목적: 주소가 비어 있거나 공백뿐이면 @NotBlank 로 걸러져 400 이 되는지 확인한다.
  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("주소가 비어 있으면 400")
  void register_blankAddress(String address) throws Exception {
    expectBadRequest(requestBody("테스트 가맹점", address), "address");
  }

  // 목적: ownerId 가 빠지면 @NotNull 로 걸러져 400 이 되는지 확인한다.
  @Test
  @DisplayName("ownerId 가 없으면 400")
  void register_missingOwnerId() throws Exception {
    expectBadRequest(
        """
        {"name": "테스트 가맹점", "address": "인천광역시 남동구 1"}
        """,
        "ownerId");
  }

  // 목적: 이름 길이 제한(100자)의 경계값을 확인한다. 100자는 통과하고 101자는 400 이어야 한다.
  @Test
  @DisplayName("이름이 100자면 201, 101자면 400")
  void register_nameSizeBoundary() throws Exception {
    given(merchantService.register(any()))
        .willReturn(new MerchantRegisterResponse(1L, "가".repeat(100), "인천광역시 남동구 1", true));

    expectCreated(requestBody("가".repeat(100), "인천광역시 남동구 1"));
    expectBadRequest(requestBody("가".repeat(101), "인천광역시 남동구 1"), "name");
  }

  // 목적: 주소 길이 제한(200자)의 경계값을 확인한다. 200자는 통과하고 201자는 400 이어야 한다.
  @Test
  @DisplayName("주소가 200자면 201, 201자면 400")
  void register_addressSizeBoundary() throws Exception {
    given(merchantService.register(any()))
        .willReturn(new MerchantRegisterResponse(1L, "테스트 가맹점", "가".repeat(200), true));

    expectCreated(requestBody("테스트 가맹점", "가".repeat(200)));
    expectBadRequest(requestBody("테스트 가맹점", "가".repeat(201)), "address");
  }

  private String requestBody(String name, String address) {
    return """
        {"ownerId": 1, "name": "%s", "address": "%s"}
        """
        .formatted(name, address);
  }

  private void expectCreated(String body) throws Exception {
    mockMvc
        .perform(post("/api/merchants").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
  }

  // 검증 실패는 공통 응답 형식(COM-001)으로 내려가고, 메시지에 실패한 필드명이 들어간다.
  // 또한 이 요청 때문에 서비스가 새로 호출되지 않았는지도 확인한다.
  private void expectBadRequest(String body, String field) throws Exception {
    int callsBefore = mockingDetails(merchantService).getInvocations().size();
    mockMvc
        .perform(post("/api/merchants").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("COM-001"))
        .andExpect(jsonPath("$.message").value(containsString(field + ":")));
    assertThat(mockingDetails(merchantService).getInvocations()).hasSize(callsBefore);
  }
}
