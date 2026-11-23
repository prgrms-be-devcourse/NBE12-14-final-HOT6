package com.back.nbe1214finalhot6.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 새 API 경로를 만들고 {@link SecurityConfig}에 규칙을 추가하는 것을 깜빡하지 않았는지 확인한다.
 *
 * <p>규칙이 없는 경로는 "로그인만 하면 누구나(운영자 포함) 접근"으로 열린다. 아무 에러도 나지 않아서 놓치기 쉬우므로, 모든 컨트롤러 경로를 사용자·점주·운영자·익명으로
 * 실제 호출해서 아래 세 가지가 아닌지 본다.
 *
 * <ul>
 *   <li>로그인 없이 열려 있는데 {@link #PUBLIC_ENDPOINTS}에 없는 경로(공개 목록의 경로는 검사하지 않는다)
 *   <li>모든 로그인 사용자가 통과하는데 {@link #ANY_LOGGED_IN_ENDPOINTS}에 없는 경로(= 규칙이 없는 경로)
 *   <li>아무도 통과하지 못하는 경로(규칙이 잘못된 경로)
 * </ul>
 *
 * <p><b>새 경로를 추가했다면 이 테스트가 실패한다.</b> {@link SecurityConfig}에 그 경로를 허용할 역할 규칙을 추가해야 한다. 의도적으로 "로그인만 하면 되는" 경로라면
 * 아래 목록에 이유와 함께 적는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SecurityRuleCoverageTest {

  /** 로그인 없이 열려 있어야 하는 경로. 새로 열 때는 보안 검토 후 추가한다. */
  private static final Set<String> PUBLIC_ENDPOINTS =
      Set.of("POST /api/auth/login", "POST /api/users/signup");

  /** 역할 구분 없이 로그인만 하면 되는 경로. 의도한 공통 경로만 보안 검토 후 추가한다. */
  private static final Set<String> ANY_LOGGED_IN_ENDPOINTS = Set.of("GET /api/users/me");

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping handlerMapping;

  @Test
  @DisplayName("모든 /api 경로가 SecurityConfig 의 명시 규칙으로 보호된다")
  void everyApiEndpointHasAnExplicitRule() throws Exception {
    List<String> problems = new ArrayList<>();
    Set<String> checked = new TreeSet<>();

    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      Set<String> patterns =
          info.getPathPatternsCondition() == null
              ? Set.of()
              : info.getPathPatternsCondition().getPatternValues();
      Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
      List<HttpMethod> httpMethods = new ArrayList<>();
      if (methods.isEmpty()) {
        httpMethods.add(HttpMethod.GET);
      } else {
        methods.forEach(m -> httpMethods.add(HttpMethod.valueOf(m.name())));
      }

      for (String pattern : patterns) {
        if (!pattern.startsWith("/api")) {
          continue; // /error 같은 프레임워크 경로
        }
        String path = pattern.replaceAll("\\{[^/}]+}", "1");
        for (HttpMethod method : httpMethods) {
          String key = method + " " + pattern;
          checked.add(key);
          if (PUBLIC_ENDPOINTS.contains(key)) {
            continue; // 공개 경로는 호출해도 컨트롤러가 스스로 401 을 줄 수 있어서(예: 쿠키 없음) 여기서는 보지 않는다. 열려 있는지는 SecurityConfigTest 가 확인한다
          }
          boolean anonymous = passes(method, path, null);
          boolean user = passes(method, path, UserRole.USER);
          boolean owner = passes(method, path, UserRole.OWNER);
          boolean admin = passes(method, path, UserRole.ADMIN);

          if (anonymous && !PUBLIC_ENDPOINTS.contains(key)) {
            problems.add(key + " : 로그인 없이 열려 있다(의도한 공개 경로가 아니면 SecurityConfig 를 고친다)");
          } else if (!anonymous && user && owner && admin && !ANY_LOGGED_IN_ENDPOINTS.contains(key)) {
            problems.add(key + " : 규칙이 없다. 모든 로그인 사용자(운영자 포함)가 접근한다 → SecurityConfig 에 허용 역할 규칙을 추가한다");
          } else if (!anonymous && !user && !owner && !admin) {
            problems.add(key + " : 아무도 접근할 수 없다(규칙이 잘못됐다)");
          }
        }
      }
    }

    assertThat(checked).as("검사한 경로가 있어야 한다").isNotEmpty();
    assertThat(problems)
        .as(
            "새 경로를 추가했다면 SecurityConfig 에 허용 역할 규칙을 추가해야 합니다.\n검사한 경로: %s",
            checked)
        .isEmpty();
  }

  // 보안을 통과했는지만 본다. 통과하면 컨트롤러가 400·404 등으로 답할 수 있으므로 401·403 이 아니면 통과로 본다.
  private boolean passes(HttpMethod method, String path, UserRole role) throws Exception {
    MockHttpServletRequestBuilder builder =
        request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}");
    if (role != null) {
      builder.header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L, role));
    }
    int status = mockMvc.perform(builder).andReturn().getResponse().getStatus();
    return status != 401 && status != 403;
  }
}
