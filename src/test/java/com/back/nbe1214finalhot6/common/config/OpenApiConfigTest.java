package com.back.nbe1214finalhot6.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** API 명세가 로그인 없이 열리고, 모든 API 가 설명을 갖추었는지 확인한다. */
@IntegrationTest
@AutoConfigureMockMvc
class OpenApiConfigTest {

  @Autowired MockMvc mockMvc;
  @Autowired JsonMapper jsonMapper;

  private JsonNode docs(String url) throws Exception {
    String body =
        mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return jsonMapper.readTree(body);
  }

  @Test
  @DisplayName("API 명세와 Swagger 화면은 로그인 없이 열린다")
  void docsAreOpenWithoutToken() throws Exception {
    docs("/v3/api-docs");
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("JWT 인증 방식이 등록되어 있고, 로그인·가입만 토큰이 필요 없다")
  void bearerSchemeAndPublicOperations() throws Exception {
    JsonNode doc = docs("/v3/api-docs");

    JsonNode scheme = doc.at("/components/securitySchemes/bearerAuth");
    assertThat(scheme.path("scheme").asString()).isEqualTo("bearer");
    assertThat(scheme.path("bearerFormat").asString()).isEqualTo("JWT");

    JsonNode login = doc.at("/paths/~1api~1auth~1login/post");
    JsonNode signup = doc.at("/paths/~1api~1users~1signup/post");
    assertThat(login.path("security").isArray()).isTrue();
    assertThat(login.path("security")).isEmpty();
    assertThat(signup.path("security")).isEmpty();
    assertThat(login.path("responses").has("401")).isFalse();

    JsonNode me = doc.at("/paths/~1api~1users~1me/get");
    assertThat(me.path("responses").has("401")).isTrue();
    assertThat(me.path("responses").has("403")).isTrue();
    assertThat(doc.at("/components/schemas/ErrorResponse/properties/code").isMissingNode())
        .isFalse();
  }

  @Test
  @DisplayName("모든 /api 경로에 요약과 설명이 있다(새 API 를 만들고 설명을 빠뜨리면 실패)")
  void everyOperationIsDocumented() throws Exception {
    JsonNode doc = docs("/v3/api-docs");
    List<String> missing = new ArrayList<>();
    int count = 0;
    for (var path : doc.path("paths").propertyNames()) {
      for (var method : doc.path("paths").path(path).propertyNames()) {
        JsonNode op = doc.path("paths").path(path).path(method);
        count++;
        String id = method.toUpperCase() + " " + path;
        if (op.path("summary").asString("").isBlank()) {
          missing.add(id + " : @Operation(summary) 없음");
        }
        if (op.path("tags").isEmpty()) {
          missing.add(id + " : @Tag 없음");
        }
      }
    }
    assertThat(count).isGreaterThan(40);
    assertThat(missing).as("설명이 빠진 API").isEmpty();
  }

  @Test
  @DisplayName("이용자·점주·운영자별로 나눠 볼 수 있다")
  void groupsSplitByAudience() throws Exception {
    JsonNode admin = docs("/v3/api-docs/4-운영자");
    JsonNode user = docs("/v3/api-docs/2-이용자");
    for (var path : admin.path("paths").propertyNames()) {
      assertThat(path).startsWith("/api/admin/");
    }
    assertThat(user.path("paths").propertyNames()).isNotEmpty();
    for (var path : user.path("paths").propertyNames()) {
      assertThat(path).doesNotStartWith("/api/admin/");
    }
  }
}
