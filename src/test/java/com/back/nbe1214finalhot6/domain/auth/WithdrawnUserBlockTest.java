package com.back.nbe1214finalhot6.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 탈퇴한 회원은 토큰이 아직 유효해도 모든 API 가 막히는지 실제 보안 설정과 DB 로 확인한다.
 *
 * <p>"탈퇴 직전에는 통과하던 요청"을 기준으로 삼는다. 필터가 탈퇴 여부를 보지 않으면 탈퇴 뒤에도 같은 토큰이 통과한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class WithdrawnUserBlockTest {

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired UserRepository userRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  private final List<Long> userIds = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    userRepository.deleteAllById(userIds);
    userIds.clear();
  }

  private User save(String email, String phone, UserRole role) {
    User user = userRepository.save(User.create("홍길동", email, phone, "pw", "pin", role));
    userIds.add(user.getId());
    return user;
  }

  private void withdraw(User user) {
    jdbcTemplate.update(
        "update users set status = 'WITHDRAWN', withdrawn_at = now() where id = ?", user.getId());
  }

  // 로그인한 회원 누구나 쓰는 가벼운 조회(이름 검색). 탈퇴 여부만 다르면 결과가 갈린다
  private ResultActions search(String token) throws Exception {
    return mockMvc.perform(
        get("/api/merchants").param("name", "x").header("Authorization", "Bearer " + token));
  }

  private String tokenOf(User user) {
    return jwtProvider.createAccessToken(user.getId(), user.getRole());
  }

  // 목적: 같은 토큰이 탈퇴 전에는 통과하다가 탈퇴한 직후부터 401(AUTH-004)로 막히는지 확인한다(토큰 만료를 기다리지 않음).
  @Test
  @DisplayName("탈퇴하는 순간부터 같은 토큰이 401(AUTH-004)로 막힌다")
  void blockedRightAfterWithdrawal() throws Exception {
    User user = save("blk1@devpay.com", "01066660001", UserRole.USER);
    String token = tokenOf(user);

    search(token).andExpect(status().isOk());

    withdraw(user);

    search(token)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-004"));
  }

  @Test
  @DisplayName("역할이 점주·운영자여도 탈퇴 상태면 막힌다")
  void blockedForAnyRole() throws Exception {
    User owner = save("blk2@devpay.com", "01066660002", UserRole.OWNER);
    User admin = save("blk3@devpay.com", "01066660003", UserRole.ADMIN);
    withdraw(owner);
    withdraw(admin);

    search(tokenOf(owner)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-004"));
    mockMvc
        .perform(
            get("/api/admin/settings").header("Authorization", "Bearer " + tokenOf(admin)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-004"));
  }

  // 목적: 활동 중인 회원과 DB 에 없는 회원(기능이 404 로 처리)은 필터가 막지 않는지 확인한다.
  @Test
  @DisplayName("활동 중인 회원은 그대로 통과하고, DB 에 없는 회원 번호는 필터가 막지 않는다")
  void activeAndUnknownUsersPass() throws Exception {
    User user = save("blk4@devpay.com", "01066660004", UserRole.USER);

    search(tokenOf(user)).andExpect(status().isOk());
    search(jwtProvider.createAccessToken(987_654_321L, UserRole.USER)).andExpect(status().isOk());
  }

  // 목적: 열린 경로(로그인·가입)는 탈퇴한 회원의 토큰을 들고 와도 토큰 때문에 거절되지 않는지 확인한다.
  // 없는 이메일로 로그인하면 토큰이 아니라 자격 증명 때문에(AUTH-003) 거절돼야 한다.
  @Test
  @DisplayName("열린 경로(로그인)는 탈퇴한 회원의 토큰이 있어도 토큰 때문에 거절되지 않는다")
  void openPathsAreNotBlocked() throws Exception {
    User user = save("blk5@devpay.com", "01066660005", UserRole.USER);
    withdraw(user);

    mockMvc
        .perform(
            post("/api/auth/login")
                .header("Authorization", "Bearer " + tokenOf(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nobody@devpay.com\", \"password\": \"Password1!\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-003"));
  }
}
