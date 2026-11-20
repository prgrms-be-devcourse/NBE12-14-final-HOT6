package com.back.nbe1214finalhot6.setting.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.dto.SettingResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingSource;
import com.back.nbe1214finalhot6.setting.exception.SettingErrorCode;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingAdminService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 인증 없이(로그인·CSRF 토큰 없이) 호출해서 임시 보안 설정(허용 경로)도 함께 확인한다.
@WebMvcTest(OperatorSettingAdminController.class)
@Import(SecurityConfig.class)
class OperatorSettingAdminControllerTest {

  private static final String KEY = "WALLET_MONTHLY_CHARGE_LIMIT";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private OperatorSettingAdminService settingService;

  private SettingResponse operatorValue(long value) {
    return new SettingResponse(
        SettingKey.WALLET_MONTHLY_CHARGE_LIMIT,
        "지갑 월 충전 한도(원)",
        value,
        SettingSource.OPERATOR,
        1_000_000L,
        1L,
        1_000_000L,
        LocalDateTime.of(2026, 11, 20, 9, 0),
        null);
  }

  // 목적: 전체 조회가 200 과 공통 응답 형식으로 내려가고 값·출처·범위 정보가 담기며, 상한이 없으면 hardMax 가 null 인지 확인한다.
  @Test
  @DisplayName("전체 조회는 200 과 설정 목록을 반환한다")
  void list_ok() throws Exception {
    given(settingService.list())
        .willReturn(
            List.of(
                SettingResponse.of(SettingKey.CASHBACK_USER_MONTHLY_LIMIT, null),
                operatorValue(700_000L)));

    mockMvc
        .perform(get("/api/admin/settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].key").value("CASHBACK_USER_MONTHLY_LIMIT"))
        .andExpect(jsonPath("$.data[0].source").value("DEFAULT"))
        .andExpect(jsonPath("$.data[0].hardMax").doesNotExist())
        .andExpect(jsonPath("$.data[1].value").value(700_000))
        .andExpect(jsonPath("$.data[1].source").value("OPERATOR"))
        .andExpect(jsonPath("$.data[1].hardMax").value(1_000_000));
  }

  // 목적: 값 변경이 200 과 변경된 설정을 반환하는지 확인한다.
  @Test
  @DisplayName("값 변경은 200 과 변경 결과를 반환한다")
  void put_ok() throws Exception {
    given(settingService.put(eq(KEY), eq(700_000L))).willReturn(operatorValue(700_000L));

    mockMvc
        .perform(
            put("/api/admin/settings/" + KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"value": 700000}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.value").value(700_000))
        .andExpect(jsonPath("$.data.key").value(KEY));
  }

  // 목적: 값이 빠지면 400 으로 걸러지는지 확인한다.
  @Test
  @DisplayName("값이 없으면 400")
  void put_missingValue() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/settings/" + KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  // 목적: 범위 밖 값이나 없는 키로 서비스가 던지는 도메인 예외가 400 과 SET-001 로 변환되는지 확인한다.
  @Test
  @DisplayName("범위 밖 값이나 없는 키는 400 과 SET-001")
  void put_invalidSetting() throws Exception {
    given(settingService.put(eq("NO_SUCH_KEY"), anyLong()))
        .willThrow(new BusinessException(SettingErrorCode.INVALID_SETTING));

    mockMvc
        .perform(
            put("/api/admin/settings/NO_SUCH_KEY")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"value": 1000}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SET-001"));
  }

  // 목적: 되돌리기가 200 과 기본값 상태의 설정을 반환하고, 없는 키는 400 과 SET-001 인지 확인한다.
  @Test
  @DisplayName("되돌리기는 200, 없는 키는 400 과 SET-001")
  void reset_okAndInvalid() throws Exception {
    given(settingService.reset(KEY))
        .willReturn(SettingResponse.of(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT, null));
    given(settingService.reset("NO_SUCH_KEY"))
        .willThrow(new BusinessException(SettingErrorCode.INVALID_SETTING));

    mockMvc
        .perform(delete("/api/admin/settings/" + KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.source").value("DEFAULT"))
        .andExpect(jsonPath("$.data.value").value(1_000_000));

    mockMvc
        .perform(delete("/api/admin/settings/NO_SUCH_KEY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SET-001"));
  }
}
