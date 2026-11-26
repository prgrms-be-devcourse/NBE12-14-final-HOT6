package com.back.nbe1214finalhot6.setting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.dto.SettingNoticeRequest;
import com.back.nbe1214finalhot6.setting.dto.SettingResponse;
import com.back.nbe1214finalhot6.setting.dto.SettingScheduleRequest;
import com.back.nbe1214finalhot6.setting.dto.SettingSource;
import com.back.nbe1214finalhot6.setting.exception.SettingErrorCode;
import com.back.nbe1214finalhot6.setting.service.OperatorSettingAdminService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 로그인한 사용자(허용 역할)로 호출해서 보안 설정을 거쳐도 통과하는지 함께 확인한다.
@WebMvcTest(OperatorSettingAdminController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN") // 보안 설정을 거쳐도 통과하도록 해당 경로의 허용 역할로 로그인한 상태로 호출한다
class OperatorSettingAdminControllerTest {

  private static final String KEY = "WALLET_MONTHLY_CHARGE_LIMIT";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
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
    given(settingService.put(eq(KEY), eq(700_000L), isNull())).willReturn(operatorValue(700_000L));

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
    given(settingService.put(eq("NO_SUCH_KEY"), anyLong(), any()))
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
    given(settingService.reset(eq(KEY), isNull()))
        .willReturn(SettingResponse.of(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT, null));
    given(settingService.reset(eq("NO_SUCH_KEY"), isNull()))
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

  // 목적: 공지를 함께 보내면 서비스에 전달되는지 확인한다.
  @Test
  @DisplayName("변경과 되돌리기는 공지를 함께 받는다")
  void put_andReset_withNotice() throws Exception {
    SettingNoticeRequest notice = new SettingNoticeRequest("제목", "본문", true, LocalDate.of(2026, 11, 30));
    given(settingService.put(KEY, 700_000L, notice)).willReturn(operatorValue(700_000L));
    given(settingService.reset(KEY, notice))
        .willReturn(SettingResponse.of(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT, null));
    String body =
        """
        {"title": "제목", "content": "본문", "showOnHome": true, "homeUntil": "2026-11-30"}
        """;

    mockMvc
        .perform(
            put("/api/admin/settings/" + KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 700000, \"notice\": " + body + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.value").value(700_000));
    mockMvc
        .perform(
            delete("/api/admin/settings/" + KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notice\": " + body + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.source").value("DEFAULT"));
  }

  // 목적: 예약이 201 과 예약 정보를 반환하고, 공지가 없으면 400 인지 확인한다.
  @Test
  @DisplayName("예약은 201 이고 공지가 없으면 400")
  void schedule() throws Exception {
    LocalDate date = LocalDate.of(2026, 12, 1);
    SettingScheduleRequest request =
        new SettingScheduleRequest(800_000L, date, new SettingNoticeRequest("제목", "본문", false, null));
    given(settingService.schedule(KEY, request))
        .willReturn(
            new SettingResponse(
                SettingKey.WALLET_MONTHLY_CHARGE_LIMIT,
                "지갑 월 충전 한도(원)",
                1_000_000L,
                SettingSource.DEFAULT,
                1_000_000L,
                1L,
                1_000_000L,
                null,
                null,
                new SettingResponse.ScheduledChange(800_000L, false, date, 5L)));

    mockMvc
        .perform(
            post("/api/admin/settings/" + KEY + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"value": 800000, "effectiveDate": "2026-12-01",
                     "notice": {"title": "제목", "content": "본문", "showOnHome": false}}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.scheduled.value").value(800_000))
        .andExpect(jsonPath("$.data.scheduled.effectiveDate").value("2026-12-01"))
        .andExpect(jsonPath("$.data.scheduled.noticeId").value(5));

    mockMvc
        .perform(
            post("/api/admin/settings/" + KEY + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"value": 800000, "effectiveDate": "2026-12-01"}
                    """))
        .andExpect(status().isBadRequest());
  }

  // 목적: 예약 취소가 서비스에 전달되고, 없으면 404(SET-003)인지 확인한다.
  @Test
  @DisplayName("예약 취소는 200, 예약이 없으면 404")
  void cancelSchedule() throws Exception {
    given(settingService.cancelSchedule(KEY))
        .willReturn(SettingResponse.of(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT, null))
        .willThrow(new BusinessException(SettingErrorCode.SCHEDULE_NOT_FOUND));

    mockMvc.perform(delete("/api/admin/settings/" + KEY + "/schedule")).andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/admin/settings/" + KEY + "/schedule"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SET-003"));
  }

  // 목적: 이용자는 예약할 수 없는지 확인한다.
  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("이용자는 예약과 취소를 할 수 없다")
  void schedule_forbiddenForUser() throws Exception {
    mockMvc
        .perform(delete("/api/admin/settings/" + KEY + "/schedule"))
        .andExpect(status().isForbidden());
  }
}
