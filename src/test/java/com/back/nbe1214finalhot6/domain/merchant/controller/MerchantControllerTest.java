package com.back.nbe1214finalhot6.domain.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.common.security.OwnedResources;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantResponse;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantUpdateRequest;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantRegistrationFacade;
import com.back.nbe1214finalhot6.domain.merchant.service.MerchantService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.RequestBuilder;

// 로그인한 사용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(MerchantController.class)
@Import(SecurityConfig.class)
@WithAuthUser(userId = 1L, role = UserRole.OWNER) // 허용 역할의 회원(1번)으로 로그인한 상태로 호출한다
class MerchantControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private MerchantService merchantService;
  @MockitoBean private MerchantRegistrationFacade registrationFacade;
  @MockitoBean private OwnedResources ownedResources;

  // 로그인한 점주의 가맹점은 1번이다
  @BeforeEach
  void myMerchantIsOne() {
    given(ownedResources.merchantIdOf(any())).willReturn(1L);
  }

  @Test
  @DisplayName("정상 요청이면 201 과 등록 결과를 반환한다")
  void 가맹점등록성공() throws Exception {
    given(registrationFacade.register(any(), any()))
        .willReturn(new MerchantRegisterResponse(1L, "테스트 가맹점", "인천광역시 남동구 1", true));

    mockMvc
        .perform(
            post("/api/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "테스트 가맹점", "address": "인천광역시 남동구 1"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.merchantId").value(1))
        .andExpect(jsonPath("$.data.cashbackEligible").value(true));
  }

  @Test
  @DisplayName("증빙 서류를 보내면 서비스에 전달되고 적립 대상으로 판정된다")
  void 증빙서류바인딩() throws Exception {
    given(registrationFacade.register(any(), any()))
        .willReturn(new MerchantRegisterResponse(1L, "서류 가맹점", "인천광역시 남동구 1", true));

    mockMvc
        .perform(
            post("/api/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "서류 가맹점", "address": "인천광역시 남동구 1",
                     "revenueProof": "revenue.pdf"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.cashbackEligible").value(true));

    ArgumentCaptor<MerchantRegisterRequest> captor =
        ArgumentCaptor.forClass(MerchantRegisterRequest.class);
    verify(registrationFacade).register(any(), captor.capture());
    assertThat(captor.getValue().revenueProof()).isEqualTo("revenue.pdf");
    assertThat(captor.getValue().resolveCashbackEligible()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("이름이 비어 있으면 400")
  void 이름이공백이면400(String name) throws Exception {
    expectBadRequest(requestBody(name, "인천광역시 남동구 1"), "name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("주소가 비어 있으면 400")
  void 주소가공백이면400(String address) throws Exception {
    expectBadRequest(requestBody("테스트 가맹점", address), "address");
  }

  @Test
  @DisplayName("등록하는 회원은 요청이 아니라 로그인한 회원이다(요청에 ownerId 를 보내도 무시한다)")
  void 등록회원은토큰의회원() throws Exception {
    given(registrationFacade.register(any(), any()))
        .willReturn(new MerchantRegisterResponse(1L, "테스트 가맹점", "인천광역시 남동구 1", true));

    mockMvc
        .perform(
            post("/api/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ownerId": 999, "name": "테스트 가맹점", "address": "인천광역시 남동구 1"}
                    """))
        .andExpect(status().isCreated());

    ArgumentCaptor<AuthUser> userCaptor = ArgumentCaptor.forClass(AuthUser.class);
    verify(registrationFacade).register(userCaptor.capture(), any());
    assertThat(userCaptor.getValue()).isEqualTo(new AuthUser(1L, UserRole.OWNER));
  }

  @Test
  @DisplayName("이미 가맹점을 등록한 회원이면 409 MER-002 이다")
  void 이미등록했으면409() throws Exception {
    given(registrationFacade.register(any(), any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_ALREADY_REGISTERED));

    mockMvc
        .perform(
            post("/api/merchants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody("테스트 가맹점", "인천광역시 남동구 1")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MER-002"));
  }

  @Test
  @DisplayName("이름이 100자면 201, 101자면 400")
  void 이름길이경계값() throws Exception {
    given(registrationFacade.register(any(), any()))
        .willReturn(new MerchantRegisterResponse(1L, "가".repeat(100), "인천광역시 남동구 1", true));

    expectCreated(requestBody("가".repeat(100), "인천광역시 남동구 1"));
    expectBadRequest(requestBody("가".repeat(101), "인천광역시 남동구 1"), "name");
  }

  @Test
  @DisplayName("주소가 200자면 201, 201자면 400")
  void 주소길이경계값() throws Exception {
    given(registrationFacade.register(any(), any()))
        .willReturn(new MerchantRegisterResponse(1L, "테스트 가맹점", "가".repeat(200), true));

    expectCreated(requestBody("테스트 가맹점", "가".repeat(200)));
    expectBadRequest(requestBody("테스트 가맹점", "가".repeat(201)), "address");
  }

  @Test
  @DisplayName("가맹점을 수정하면 200 과 수정 결과를 반환한다")
  void 가맹점수정성공() throws Exception {
    given(merchantService.update(eq(1L), any()))
        .willReturn(new MerchantResponse(1L, "새 이름", "인천광역시 남동구 1", true));

    mockMvc
        .perform(
            patch("/api/merchants/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "새 이름"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.merchantId").value(1))
        .andExpect(jsonPath("$.data.name").value("새 이름"));
  }

  @Test
  @DisplayName("일부 필드만 보내면 나머지는 null 로 전달된다")
  void 일부필드만수정시나머지는null() throws Exception {
    given(merchantService.update(eq(1L), any()))
        .willReturn(new MerchantResponse(1L, "테스트 가맹점", "인천광역시 남동구 1", true));

    mockMvc
        .perform(
            patch("/api/merchants/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"address": "새 주소"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.merchantId").value(1));

    ArgumentCaptor<MerchantUpdateRequest> captor =
        ArgumentCaptor.forClass(MerchantUpdateRequest.class);
    verify(merchantService).update(eq(1L), captor.capture());
    assertThat(captor.getValue().name()).isNull();
    assertThat(captor.getValue().address()).isEqualTo("새 주소");
  }

  @Test
  @DisplayName("존재하지 않는 가맹점을 수정하면 404")
  void 없는가맹점수정시404() throws Exception {
    given(ownedResources.merchantIdOf(any())).willReturn(999L);
    given(merchantService.update(eq(999L), any()))
        .willThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

    mockMvc
        .perform(
            patch("/api/merchants/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "새 이름"}
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("MER-001"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("수정할 이름이 비어 있으면 400")
  void 수정할이름이공백이면400(String name) throws Exception {
    expectInvalidRequest(patchRequest("{\"name\": \"%s\"}".formatted(name)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("수정할 주소가 비어 있으면 400")
  void 수정할주소가공백이면400(String address) throws Exception {
    expectInvalidRequest(patchRequest("{\"address\": \"%s\"}".formatted(address)));
  }

  @Test
  @DisplayName("수정할 이름은 100자, 주소는 200자까지 허용한다")
  void 수정길이경계값() throws Exception {
    given(merchantService.update(eq(1L), any()))
        .willReturn(new MerchantResponse(1L, "테스트 가맹점", "인천광역시 남동구 1", true));

    mockMvc.perform(patchRequest("{\"name\": \"%s\"}".formatted("가".repeat(100))))
        .andExpect(status().isOk());
    mockMvc.perform(patchRequest("{\"address\": \"%s\"}".formatted("가".repeat(200))))
        .andExpect(status().isOk());
    expectInvalidRequest(patchRequest("{\"name\": \"%s\"}".formatted("가".repeat(101))));
    expectInvalidRequest(patchRequest("{\"address\": \"%s\"}".formatted("가".repeat(201))));
  }

  @Test
  @DisplayName("이름으로 검색하면 200 과 목록·페이지 정보를 반환한다")
  void 이름검색성공() throws Exception {
    given(merchantService.search("분식", 0, 20))
        .willReturn(
            new PageResponse<>(
                List.of(
                    new MerchantResponse(1L, "행복분식", "인천광역시 남동구 1", true),
                    new MerchantResponse(2L, "해피분식", "인천광역시 서구 2", false)),
                0,
                20,
                2,
                1));

    mockMvc
        .perform(get("/api/merchants").param("name", "분식"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].name").value("행복분식"))
        .andExpect(jsonPath("$.data.content[0].cashbackEligible").value(true))
        .andExpect(jsonPath("$.data.content[1].cashbackEligible").value(false))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(1));
  }

  @Test
  @DisplayName("page 와 size 는 기본값 0, 20 이고 값을 보내면 그대로 전달된다")
  void 검색페이징파라미터전달() throws Exception {
    given(merchantService.search(any(), eq(0), eq(20)))
        .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    given(merchantService.search(any(), eq(2), eq(10)))
        .willReturn(new PageResponse<>(List.of(), 2, 10, 0, 0));

    mockMvc.perform(get("/api/merchants").param("name", "분식")).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/merchants").param("name", "분식").param("page", "2").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.size").value(10));

    verify(merchantService).search("분식", 0, 20);
    verify(merchantService).search("분식", 2, 10);
  }

  @Test
  @DisplayName("잘못된 검색 요청이면 400")
  void 잘못된검색요청이면400() throws Exception {
    expectInvalidRequest(get("/api/merchants"));
    expectInvalidRequest(get("/api/merchants").param("name", "   "));
    expectInvalidRequest(get("/api/merchants").param("name", "분식").param("page", "-1"));
    expectInvalidRequest(get("/api/merchants").param("name", "분식").param("size", "0"));
  }

  private RequestBuilder patchRequest(String body) {
    return patch("/api/merchants/me").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  // 요청이 400(COM-001)으로 거절되고, 서비스가 새로 호출되지 않았는지 확인한다.
  private void expectInvalidRequest(RequestBuilder request) throws Exception {
    int callsBefore = mockingDetails(merchantService).getInvocations().size();
    mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("COM-001"));
    assertThat(mockingDetails(merchantService).getInvocations()).hasSize(callsBefore);
  }

  private String requestBody(String name, String address) {
    return """
        {"name": "%s", "address": "%s"}
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
    int callsBefore = mockingDetails(registrationFacade).getInvocations().size();
    mockMvc
        .perform(post("/api/merchants").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("COM-001"))
        .andExpect(jsonPath("$.message").value(containsString(field + ":")));
    assertThat(mockingDetails(registrationFacade).getInvocations()).hasSize(callsBefore);
  }
}
