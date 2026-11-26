package com.back.nbe1214finalhot6.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 결제에서 PIN 을 강제하는지 실제 보안 설정, 실제 PIN 서비스, 실제 DB 로 확인한다.
 *
 * <p>결제 처리(PaymentFacade)만 가짜로 두어서 "결제가 시작됐는지"를 호출 여부로 본다. 실패 횟수는 새 트랜잭션으로 저장되므로 테스트 전체를 트랜잭션으로 감싸지 않고, 만든
 * 회원과 지갑만 직접 지운다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PaymentPinEnforcementTest {

  private static final String PIN = "123456";

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired UserRepository userRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired MutableClock clock;
  @MockitoBean PaymentFacade paymentFacade;

  private User user;
  private Wallet wallet;

  @BeforeEach
  void setUp() {
    user =
        userRepository.save(
            User.create(
                "홍길동", "pay-pin@devpay.com", "01033330001", "pw", passwordEncoder.encode(PIN), UserRole.USER));
    wallet = walletRepository.save(Wallet.create(user.getId(), LocalDateTime.of(2026, 11, 24, 9, 0)));
  }

  @AfterEach
  void cleanUp() {
    walletRepository.deleteById(wallet.getId());
    userRepository.deleteById(user.getId());
  }

  private ResultActions pay(String body) throws Exception {
    return mockMvc.perform(
        post("/api/payments")
            .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId(), UserRole.USER))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private int failCount() {
    return jdbcTemplate.queryForObject(
        "select pin_fail_count from users where id = ?", Integer.class, user.getId());
  }

  // 목적: 토큰만 있고 PIN 을 모르면 결제를 시작할 수 없는지 확인한다. PIN 이 틀린 요청은 결제도, 결제 기록도 만들지 않는다.
  @Test
  @DisplayName("PIN 이 틀리면 결제를 시작하지 않고 실패 횟수만 오른다")
  void wrongPinDoesNotStartPayment() throws Exception {
    pay("{\"qrToken\": \"qr-1\", \"cashbackUsed\": 0, \"pin\": \"000000\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH-005"))
        .andExpect(jsonPath("$.message").value("PIN이 일치하지 않습니다 (잔여 4회)"));

    verify(paymentFacade, never()).pay(any(), any());
    assertThat(failCount()).isEqualTo(1);
  }

  // 목적: PIN 이 아예 없는 요청(토큰만으로 결제하려는 시도)이 결제도, PIN 시도 횟수도 건드리지 않고 거절되는지 확인한다.
  @Test
  @DisplayName("PIN 이 없는 결제 요청은 400 이고 결제를 시작하지 않는다")
  void missingPinIsRejected() throws Exception {
    pay("{\"qrToken\": \"qr-1\", \"cashbackUsed\": 0}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COM-001"));

    verify(paymentFacade, never()).pay(any(), any());
    assertThat(failCount()).isZero();
  }

  // 목적: 맞는 PIN 이면 내 지갑(토큰의 회원)으로 결제가 시작되고, 쌓인 실패 횟수가 0으로 돌아가는지 확인한다.
  @Test
  @DisplayName("맞는 PIN 이면 내 지갑으로 결제가 시작되고 실패 횟수가 0이 된다")
  void correctPinStartsPayment() throws Exception {
    given(paymentFacade.pay(any(), any())).willReturn(null);
    pay("{\"qrToken\": \"qr-1\", \"cashbackUsed\": 0, \"pin\": \"000000\"}")
        .andExpect(status().isBadRequest());
    assertThat(failCount()).isEqualTo(1);

    pay("{\"qrToken\": \"qr-1\", \"cashbackUsed\": 500, \"pin\": \"" + PIN + "\"}")
        .andExpect(status().isOk());

    verify(paymentFacade).pay(any(), org.mockito.ArgumentMatchers.eq(new PaymentRequest(wallet.getId(), "qr-1", 500L)));
    assertThat(failCount()).isZero();
  }

  // 목적: 5번 틀려 잠기면, 그 뒤에는 맞는 PIN 으로도 결제를 시작할 수 없는지 확인한다.
  @Test
  @DisplayName("5번 틀려 잠기면 맞는 PIN 으로도 결제할 수 없다(409)")
  void lockedUserCannotPay() throws Exception {
    for (int i = 0; i < 4; i++) {
      pay("{\"qrToken\": \"qr-1\", \"pin\": \"000000\"}").andExpect(status().isBadRequest());
    }
    pay("{\"qrToken\": \"qr-1\", \"pin\": \"000000\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-006"));

    pay("{\"qrToken\": \"qr-1\", \"pin\": \"" + PIN + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH-006"));

    verify(paymentFacade, never()).pay(any(), any());
    assertThat(failCount()).isEqualTo(5);
  }

  // 목적: 잠긴 지 10분이 지나면 다시 결제할 수 있는지 확인한다(임시 방식, MVP 이후 이메일 인증 해제(U7)로 대체).
  @Test
  @DisplayName("잠긴 지 10분이 지나면 맞는 PIN 으로 다시 결제할 수 있다")
  void canPayAgainAfterLockExpires() throws Exception {
    for (int i = 0; i < 5; i++) {
      pay("{\"qrToken\": \"qr-1\", \"pin\": \"000000\"}");
    }
    pay("{\"qrToken\": \"qr-1\", \"pin\": \"" + PIN + "\"}").andExpect(status().isConflict());
    verify(paymentFacade, never()).pay(any(), any());

    clock.advance(Duration.ofMinutes(10));
    pay("{\"qrToken\": \"qr-1\", \"cashbackUsed\": 0, \"pin\": \"" + PIN + "\"}")
        .andExpect(status().isOk());

    verify(paymentFacade).pay(any(), any());
    assertThat(failCount()).isZero();
  }
}
