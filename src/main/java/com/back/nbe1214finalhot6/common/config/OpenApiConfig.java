package com.back.nbe1214finalhot6.common.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 명세(Swagger UI)의 공통 설정.
 *
 * <ul>
 *   <li>JWT 로 인증하는 API 이므로 화면 오른쪽 위 Authorize 에 로그인 응답의 {@code accessToken} 을 넣으면 모든 요청에 붙는다.
 *   <li>로그인·가입처럼 토큰이 필요 없는 API 는 잠금 표시 없이 보인다({@link #PUBLIC_OPERATIONS}).
 *   <li>모든 응답은 {@code ApiResponse} 로 감싸 오고, 실패할 때의 공통 응답(400, 401, 403)을 각 API 에 자동으로 붙인다.
 *   <li>사용하는 쪽(이용자·점주·운영자)별로 API 를 나눠 볼 수 있다.
 * </ul>
 *
 * 운영 환경에서는 명세를 공개하지 않는다(application-prod.yaml).
 */
@Configuration
public class OpenApiConfig {

  static final String BEARER = "bearerAuth";

  /** 토큰 없이 부를 수 있는 API. {@link SecurityConfig} 의 "누구나" 규칙과 같아야 한다. */
  private static final Set<String> PUBLIC_OPERATIONS =
      Set.of(
          "POST /api/auth/login", "POST /api/users/signup", "POST /api/owners/signup");

  /** 실패했을 때의 응답 본문. 성공 응답과 같은 틀({@code success/code/message/data})이다. */
  @Schema(name = "ErrorResponse", description = "실패 응답. 성공 응답과 같은 틀이고 data 는 null 이다")
  record ErrorResponse(
      @Schema(description = "항상 false", example = "false") boolean success,
      @Schema(description = "오류 코드(도메인별 접두어와 번호)", example = "COM-006") String code,
      @Schema(description = "사용자에게 보여 줄 수 있는 오류 설명", example = "유효하지 않은 토큰입니다")
          String message,
      @Schema(description = "항상 null", nullable = true) Object data) {}

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("DevPay API")
                .version("v1")
                .description(
                    """
                    캐시백 QR 결제 서비스 DevPay 의 API 명세입니다.

                    **인증**: `POST /api/auth/login` 으로 받은 `accessToken` 을 오른쪽 위 **Authorize** 에 넣으면 이후 요청에 자동으로 붙습니다.
                    역할은 이용자(USER), 점주(OWNER, 이용자 기능 포함), 운영자(ADMIN, 운영 전용)입니다.

                    **응답**: 성공과 실패 모두 `{ success, code, message, data }` 로 감싸 옵니다. 실패하면 `success=false` 이고 `code` 가 오류 종류입니다.

                    **금액**: 모든 금액은 원 단위 정수입니다. 날짜·시각은 한국 시간(KST) 기준입니다.
                    """))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인 응답의 accessToken (앞의 `Bearer ` 는 붙이지 않는다)")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }

  /** 공통 오류 응답을 등록하고 각 API 에 붙인다. 토큰이 필요 없는 API 는 잠금 표시를 뺀다. */
  @Bean
  public OpenApiCustomizer commonResponsesCustomizer() {
    return openApi -> {
      if (openApi.getComponents() == null) {
        openApi.setComponents(new Components());
      }
      ModelConverters.getInstance()
          .readAll(ErrorResponse.class)
          .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));

      if (openApi.getPaths() == null) {
        return;
      }
      openApi
          .getPaths()
          .forEach(
              (path, item) ->
                  item.readOperationsMap()
                      .forEach(
                          (method, operation) -> {
                            boolean isPublic = PUBLIC_OPERATIONS.contains(method + " " + path);
                            if (isPublic) {
                              operation.setSecurity(List.of());
                            }
                            addCommon(operation, isPublic);
                          }));
    };
  }

  private static void addCommon(Operation operation, boolean isPublic) {
    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      responses = new ApiResponses();
      operation.setResponses(responses);
    }
    boolean hasInput =
        operation.getRequestBody() != null
            || (operation.getParameters() != null && !operation.getParameters().isEmpty());
    if (hasInput) {
      responses.addApiResponse(
          "400", error("입력값이 올바르지 않습니다(검증 실패, 누락된 값, 형식 오류). code 와 message 에 사유가 있다"));
    }
    if (!isPublic) {
      responses.addApiResponse("401", error("토큰이 없거나 만료되었거나 올바르지 않다(COM-006)"));
      responses.addApiResponse("403", error("이 API 를 쓸 수 없는 역할이다(이용자·점주·운영자 구분은 상단 설명 참고)"));
    }
    responses.addApiResponse("500", error("서버 내부 오류"));
  }

  private static ApiResponse error(String description) {
    return new ApiResponse()
        .description(description)
        .content(
            new Content()
                .addMediaType(
                    "application/json",
                    new MediaType()
                        .schema(
                            new io.swagger.v3.oas.models.media.Schema<>()
                                .$ref("#/components/schemas/ErrorResponse"))));
  }

  // ---- 화면에서 API 를 쓰는 쪽별로 나눠 보기 ----

  @Bean
  public GroupedOpenApi allApi() {
    return GroupedOpenApi.builder().group("0-전체").pathsToMatch("/api/**").build();
  }

  @Bean
  public GroupedOpenApi authApi() {
    return GroupedOpenApi.builder()
        .group("1-인증·회원")
        .pathsToMatch("/api/auth/**", "/api/users/**")
        .build();
  }

  @Bean
  public GroupedOpenApi userApi() {
    return GroupedOpenApi.builder()
        .group("2-이용자")
        .pathsToMatch(
            "/api/wallet/**", "/api/payments/**", "/api/qr/**", "/api/cashback/**", "/api/notices/**")
        .build();
  }

  @Bean
  public GroupedOpenApi merchantApi() {
    return GroupedOpenApi.builder().group("3-점주").pathsToMatch("/api/merchants/**").build();
  }

  @Bean
  public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder().group("4-운영자").pathsToMatch("/api/admin/**").build();
  }
}
