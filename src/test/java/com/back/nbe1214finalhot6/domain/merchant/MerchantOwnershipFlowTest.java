package com.back.nbe1214finalhot6.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점주 기능(수정·QR·정산)이 요청의 가맹점 번호가 아니라 토큰의 회원으로 가맹점을 찾는지, 실제 보안 설정과 DB 로 끝까지 확인한다.
 *
 * <p>가맹점은 DB 에 직접 넣고 점주 토큰은 직접 발급한다. 테스트 전체를 한 트랜잭션으로 감싸서(끝나면 롤백) 따로 정리하지 않는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MerchantOwnershipFlowTest {

  private static final long A_USER = 9_801L;
  private static final long B_USER = 9_802L;
  private static final long NO_MERCHANT_USER = 9_803L;

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired MerchantRepository merchantRepository;

  // 목적: /me 로 수정하면 토큰의 점주 가맹점만 바뀌고, 남의 가맹점 번호를 경로에 쓰던 옛 방식은 더 이상 없는지 확인한다.
  @Test
  @DisplayName("점주는 자기 가맹점만 다룬다. 남의 가맹점 번호를 경로에 써도 닿지 않는다")
  void ownersOnlyTouchOwnMerchant() throws Exception {
    long aId = saveMerchant(A_USER, "A 가게");
    long bId = saveMerchant(B_USER, "B 가게");
    String aToken = ownerToken(A_USER);

    // /me 로 수정하면 A 의 가맹점만 바뀐다
    mockMvc
        .perform(patchMe(aToken, "{\"name\": \"A 새 이름\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.merchantId").value(aId))
        .andExpect(jsonPath("$.data.name").value("A 새 이름"));
    assertThat(nameOf(aId)).isEqualTo("A 새 이름");
    assertThat(nameOf(bId)).isEqualTo("B 가게");

    // 남의 가맹점 번호를 경로에 쓰는 옛 방식은 없다(수정·QR·정산 모두 4xx 이고 B 의 가맹점은 그대로)
    mockMvc
        .perform(
            patch("/api/merchants/{id}", bId)
                .header("Authorization", "Bearer " + aToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"탈취\"}"))
        .andExpect(status().is4xxClientError());
    mockMvc
        .perform(
            post("/api/merchants/{id}/qr/dynamic", bId)
                .header("Authorization", "Bearer " + aToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 5000}"))
        .andExpect(status().is4xxClientError());
    mockMvc
        .perform(
            get("/api/merchants/{id}/settlements/daily", bId)
                .header("Authorization", "Bearer " + aToken)
                .param("from", "2026-11-01")
                .param("to", "2026-11-02"))
        .andExpect(status().is4xxClientError());
    assertThat(nameOf(bId)).isEqualTo("B 가게");

    // 내 가맹점의 QR 생성·정산 조회는 된다
    mockMvc
        .perform(
            post("/api/merchants/me/qr/dynamic")
                .header("Authorization", "Bearer " + aToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 5000}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            get("/api/merchants/me/settlements/daily")
                .header("Authorization", "Bearer " + aToken)
                .param("from", "2026-11-01")
                .param("to", "2026-11-02"))
        .andExpect(status().isOk());
  }

  // 목적: 점주 역할이어도 내 가맹점이 없으면(공용 도구가 MER-001) 404 이고, 남의 가맹점으로 대신 처리되지 않는지 확인한다.
  @Test
  @DisplayName("내 가맹점이 없는 점주는 404 이고 다른 가맹점은 바뀌지 않는다")
  void ownerWithoutMerchantGets404() throws Exception {
    long bId = saveMerchant(B_USER, "B 가게");

    mockMvc
        .perform(patchMe(ownerToken(NO_MERCHANT_USER), "{\"name\": \"탈취\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));
    mockMvc
        .perform(
            post("/api/merchants/me/qr/dynamic")
                .header("Authorization", "Bearer " + ownerToken(NO_MERCHANT_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 5000}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));

    assertThat(nameOf(bId)).isEqualTo("B 가게");
  }

  private long saveMerchant(long ownerId, String name) {
    return merchantRepository.save(Merchant.create(ownerId, name, "인천광역시 남동구 1", true)).getId();
  }

  private String nameOf(long merchantId) {
    return merchantRepository.findById(merchantId).orElseThrow().getName();
  }

  private String ownerToken(long userId) {
    return jwtProvider.createAccessToken(userId, UserRole.OWNER);
  }

  private MockHttpServletRequestBuilder patchMe(String token, String body) {
    return patch("/api/merchants/me")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }
}
