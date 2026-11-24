package com.back.nbe1214finalhot6.domain.settlement.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.common.config.SecurityConfig;
import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.settlement.dto.MonthlySettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.dto.SettlementRunResult;
import com.back.nbe1214finalhot6.domain.settlement.exception.SettlementErrorCode;
import com.back.nbe1214finalhot6.domain.settlement.service.SettlementRunService;
import com.back.nbe1214finalhot6.support.WithAuthUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SettlementAdminController.class)
@Import({SecurityConfig.class, SettlementAdminControllerTest.FixedClockConfig.class})
@WithAuthUser(role = UserRole.ADMIN) // 허용 역할의 운영자로 로그인한 상태로 호출한다
class SettlementAdminControllerTest {

  // 2026-11-23 09:00 KST. 어제는 11/22, 지난달은 10월이다.
  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    }
  }

  @Autowired private MockMvc mockMvc;
  @MockitoBean private JwtProvider jwtProvider; // 보안 설정이 토큰 검사에 쓴다
  @MockitoBean private SettlementRunService runService;

  @Test
  @DisplayName("날짜를 지정하면 그 날짜의 일 정산을 실행하고 결과를 반환한다")
  void 일정산_날짜지정() throws Exception {
    LocalDate date = LocalDate.of(2026, 11, 20);
    given(runService.runDaily(date)).willReturn(new SettlementRunResult(date, 2, 1, 0));

    mockMvc
        .perform(post("/api/admin/settlements/daily/run").param("date", "2026-11-20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.date").value("2026-11-20"))
        .andExpect(jsonPath("$.data.created").value(2))
        .andExpect(jsonPath("$.data.skipped").value(1));
  }

  @Test
  @DisplayName("날짜를 생략하면 어제의 일 정산을 실행한다")
  void 일정산_날짜생략() throws Exception {
    LocalDate yesterday = LocalDate.of(2026, 11, 22);
    given(runService.runDaily(yesterday))
        .willReturn(new SettlementRunResult(yesterday, 1, 0, 0));

    mockMvc
        .perform(post("/api/admin/settlements/daily/run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.date").value("2026-11-22"));
  }

  @Test
  @DisplayName("오늘 이후 날짜면 400 STL-001 로 응답한다")
  void 일정산_오늘이후() throws Exception {
    LocalDate today = LocalDate.of(2026, 11, 23);
    given(runService.runDaily(today))
        .willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_DATE_NOT_ALLOWED));

    mockMvc
        .perform(post("/api/admin/settlements/daily/run").param("date", "2026-11-23"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STL-001"));
  }

  @Test
  @DisplayName("날짜 형식이 올바르지 않으면 400 COM-001 로 응답한다")
  void 일정산_날짜형식오류() throws Exception {
    mockMvc
        .perform(post("/api/admin/settlements/daily/run").param("date", "2026/11/20"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));
  }

  @Test
  @DisplayName("달을 지정하면 그 달의 월 정산을 실행하고 결과를 반환한다")
  void 월정산_달지정() throws Exception {
    YearMonth month = YearMonth.of(2026, 9);
    given(runService.runMonthly(month))
        .willReturn(new MonthlySettlementRunResult(month, 3, 3, 0));

    mockMvc
        .perform(post("/api/admin/settlements/monthly/run").param("month", "2026-09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.month").value("2026-09"))
        .andExpect(jsonPath("$.data.created").value(3))
        .andExpect(jsonPath("$.data.paid").value(3));
  }

  @Test
  @DisplayName("달을 생략하면 지난달의 월 정산을 실행한다")
  void 월정산_달생략() throws Exception {
    YearMonth lastMonth = YearMonth.of(2026, 10);
    given(runService.runMonthly(lastMonth))
        .willReturn(new MonthlySettlementRunResult(lastMonth, 0, 0, 0));

    mockMvc
        .perform(post("/api/admin/settlements/monthly/run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.month").value("2026-10"));
  }

  @Test
  @DisplayName("해당 월의 일 정산이 끝나지 않았으면 409 STL-005 로 응답한다")
  void 월정산_일정산미완료() throws Exception {
    YearMonth october = YearMonth.of(2026, 10);
    given(runService.runMonthly(october))
        .willThrow(new BusinessException(SettlementErrorCode.DAILY_SETTLEMENT_INCOMPLETE));

    mockMvc
        .perform(post("/api/admin/settlements/monthly/run").param("month", "2026-10"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STL-005"));
  }

  @Test
  @DisplayName("이번 달 이후면 400 STL-003 으로 응답한다")
  void 월정산_이번달이후() throws Exception {
    YearMonth thisMonth = YearMonth.of(2026, 11);
    given(runService.runMonthly(thisMonth))
        .willThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_MONTH_NOT_ALLOWED));

    mockMvc
        .perform(post("/api/admin/settlements/monthly/run").param("month", "2026-11"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STL-003"));
  }
}
