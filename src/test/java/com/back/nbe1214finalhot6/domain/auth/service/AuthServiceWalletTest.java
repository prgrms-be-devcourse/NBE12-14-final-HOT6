package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.back.nbe1214finalhot6.domain.auth.dto.SignupRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupResponse;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletStatus;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletOpeningService;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 가입과 모의계좌·지갑 생성이 한 트랜잭션으로 묶이는지 확인한다. */
@IntegrationTest
class AuthServiceWalletTest {

  @Autowired AuthService authService;
  @Autowired UserRepository userRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired AccountTransactionRepository accountTransactionRepository;
  @Autowired WalletRepository walletRepository;

  // 실제 동작은 그대로 두고, 실패 상황만 만들기 위한 spy
  @MockitoSpyBean WalletOpeningService walletOpeningService;

  // 테스트가 DB에 남긴 행 정리(원장 → 지갑 → 계좌 → 회원 순)
  @AfterEach
  void cleanUp() {
    accountTransactionRepository.deleteAll();
    walletRepository.deleteAll();
    accountRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("가입하면 모의계좌(200만원), 지갑(잔액 0, ACTIVE), 가입 보너스 원장이 함께 만들어진다")
  void signup_opensAccountAndWallet() {
    SignupResponse response = authService.signup(request("open@devpay.com", "01011112222"));

    Long userId = response.id();
    Account account = accountRepository.findByUserId(userId).orElseThrow();
    assertThat(account.getBalance()).isEqualTo(2_000_000L);
    assertThat(
            accountTransactionRepository.findAll().stream()
                .filter(t -> t.getAccountId().equals(account.getId())))
        .hasSize(1);

    Wallet wallet = walletRepository.findByUserId(userId).orElseThrow();
    assertThat(wallet.getBalance()).isZero();
    assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
  }

  @Test
  @DisplayName("계좌·원장·지갑을 만든 뒤 실패해도 회원 저장까지 모두 롤백된다")
  void signup_rollsBackEverythingWhenOpeningFails() {
    long accounts = accountRepository.count();
    long ledgers = accountTransactionRepository.count();
    long wallets = walletRepository.count();
    // 실제로 계좌·원장·지갑을 모두 만든 직후에 예외를 던져, 만들어진 것까지 롤백되는지 확인
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("지갑 개설 직후 실패");
            })
        .when(walletOpeningService)
        .open(any());

    assertThatThrownBy(() -> authService.signup(request("fail@devpay.com", "01033334444")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(userRepository.existsByEmail("fail@devpay.com")).isFalse();
    assertThat(accountRepository.count()).isEqualTo(accounts);
    assertThat(accountTransactionRepository.count()).isEqualTo(ledgers);
    assertThat(walletRepository.count()).isEqualTo(wallets);
  }

  private SignupRequest request(String email, String phone) {
    return new SignupRequest("홍길동", email, "Passw0rd!", phone, "123456");
  }
}
