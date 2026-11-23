package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 가맹점 등록과 점주 승격이 한 트랜잭션으로 묶이는지, 점주당 가맹점 하나가 지켜지는지 실제 DB 로 확인한다. */
@IntegrationTest
class MerchantRegistrationFacadeTest {

  @Autowired MerchantRegistrationFacade facade;
  @Autowired UserRepository userRepository;
  @Autowired MerchantRepository merchantRepository;
  @Autowired MerchantWalletRepository merchantWalletRepository;
  @Autowired MerchantAccountRepository merchantAccountRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  // 이 테스트가 만든 회원 번호. 정리와 개수 비교를 이 테스트가 만든 것으로만 한정한다(다른 테스트가 남긴 데이터와 섞이지 않게)
  private final List<Long> userIds = new ArrayList<>();
  private long merchantsBefore;
  private long walletsBefore;
  private long accountsBefore;

  @BeforeEach
  void countBefore() {
    merchantsBefore = merchantRepository.count();
    walletsBefore = merchantWalletRepository.count();
    accountsBefore = merchantAccountRepository.count();
  }

  @AfterEach
  void cleanUp() {
    for (Long userId : userIds) {
      merchantRepository
          .findByOwnerId(userId)
          .ifPresent(
              merchant -> {
                merchantAccountRepository.findByMerchantId(merchant.getId()).ifPresent(merchantAccountRepository::delete);
                merchantWalletRepository.findByMerchantId(merchant.getId()).ifPresent(merchantWalletRepository::delete);
                merchantRepository.delete(merchant);
              });
    }
    userRepository.deleteAllById(userIds);
    userIds.clear();
  }

  // 테스트 시작 뒤 새로 생긴 개수
  private long newMerchants() {
    return merchantRepository.count() - merchantsBefore;
  }

  private long newWallets() {
    return merchantWalletRepository.count() - walletsBefore;
  }

  private long newAccounts() {
    return merchantAccountRepository.count() - accountsBefore;
  }

  // 목적: 등록하면 가맹점이 만들어지고, 등록한 회원이 USER → OWNER 로 승격되는지 확인한다.
  @Test
  @DisplayName("가맹점을 등록하면 가맹점이 만들어지고 등록한 회원이 점주가 된다")
  void registerPromotesOwner() {
    User user = save("a@devpay.com", "01011110001", UserRole.USER);

    MerchantRegisterResponse response = facade.register(authUser(user), request());

    Merchant merchant = merchantRepository.findById(response.merchantId()).orElseThrow();
    assertThat(merchant.getOwnerId()).isEqualTo(user.getId());
    assertThat(roleOf(user.getId())).isEqualTo("OWNER");
  }

  // 목적: 점주당 가맹점은 하나다. 이미 점주인 회원이 다시 등록하면 거절하고 첫 가맹점은 그대로여야 한다.
  @Test
  @DisplayName("이미 가맹점이 있는 점주가 다시 등록하면 MER-002 이고 가맹점은 하나뿐이다")
  void secondRegistrationIsRejected() {
    User user = save("a@devpay.com", "01011110001", UserRole.USER);
    MerchantRegisterResponse first = facade.register(authUser(user), request());

    assertFails(() -> facade.register(authUser(user), request()), MerchantErrorCode.MERCHANT_ALREADY_REGISTERED);

    assertThat(newMerchants()).isEqualTo(1);
    assertThat(merchantRepository.findByOwnerId(user.getId()))
        .hasValueSatisfying(m -> assertThat(m.getId()).isEqualTo(first.merchantId()));
    assertThat(roleOf(user.getId())).isEqualTo("OWNER");
  }

  // 목적: 등록이 거절되면 승격도 함께 취소되는지 확인한다(함께 성공하거나 함께 취소).
  // 역할이 USER 인데 가맹점이 이미 있는 상태(예: 역할만 되돌려진 경우)를 만들어, 승격이 일어난 뒤 등록이 실패하게 한다.
  @Test
  @DisplayName("등록이 거절되면 승격도 취소되어 역할이 그대로다")
  void promotionRollsBackWhenRegistrationFails() {
    User user = save("a@devpay.com", "01011110001", UserRole.USER);
    merchantRepository.save(Merchant.create(user.getId(), "기존 가맹점", "서울", true));

    assertFails(() -> facade.register(authUser(user), request()), MerchantErrorCode.MERCHANT_ALREADY_REGISTERED);

    assertThat(roleOf(user.getId())).isEqualTo("USER");
    assertThat(newMerchants()).isEqualTo(1); // 미리 넣어 둔 기존 가맹점 하나뿐
  }

  @Test
  @DisplayName("운영자는 등록할 수 없고(COM-008) 가맹점·지갑·계좌가 하나도 남지 않는다")
  void adminCannotRegister() {
    User admin = save("admin@devpay.com", "01011110003", UserRole.ADMIN);

    assertFails(() -> facade.register(authUser(admin), request()), CommonErrorCode.FORBIDDEN);

    assertThat(newMerchants()).isZero();
    assertThat(newWallets()).isZero();
    assertThat(newAccounts()).isZero();
    assertThat(roleOf(admin.getId())).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("없는 회원이면 COM-009 이고 가맹점이 만들어지지 않는다")
  void unknownUser() {
    AuthUser ghost = new AuthUser(999_999L, UserRole.USER);

    assertFails(() -> facade.register(ghost, request()), CommonErrorCode.RESOURCE_NOT_FOUND);

    assertThat(newMerchants()).isZero();
  }

  // 목적: 같은 회원이 동시에 두 번 눌러도(사전 확인을 둘 다 통과해도) 가맹점은 하나이고 한쪽은 MER-002 인지 확인한다.
  // USER 로 시작하면 승격 갱신에서, OWNER 로 시작하면 DB 유일 제약에서 두 번째 요청이 막힌다.
  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"USER", "OWNER"})
  @DisplayName("같은 회원이 동시에 두 번 등록해도 가맹점은 하나이고 한쪽은 MER-002 이다")
  void concurrentRegistration(UserRole startRole) throws Exception {
    User user = save("a@devpay.com", "01011110001", startRole);
    AuthUser authUser = authUser(user);
    CyclicBarrier startTogether = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Object>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        futures.add(
            pool.submit(
                () -> {
                  startTogether.await(5, TimeUnit.SECONDS);
                  try {
                    return facade.register(authUser, request());
                  } catch (BusinessException e) {
                    return e;
                  }
                }));
      }

      int success = 0;
      for (Future<Object> future : futures) {
        Object result = future.get(30, TimeUnit.SECONDS);
        if (result instanceof BusinessException e) {
          assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_ALREADY_REGISTERED);
        } else {
          success++;
        }
      }
      assertThat(success).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }

    assertThat(newMerchants()).isEqualTo(1);
    assertThat(newWallets()).isEqualTo(1);
    assertThat(roleOf(user.getId())).isEqualTo("OWNER");
  }

  private void assertFails(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  private MerchantRegisterRequest request() {
    return new MerchantRegisterRequest("테스트 가맹점", "인천광역시 남동구 1", null);
  }

  private AuthUser authUser(User user) {
    return new AuthUser(user.getId(), user.getRole());
  }

  private String roleOf(Long userId) {
    return jdbcTemplate.queryForObject("select role from users where id = ?", String.class, userId);
  }

  private User save(String email, String phone, UserRole role) {
    User user = userRepository.save(User.create("홍길동", email, phone, "pw-hash", "pin-hash", role));
    userIds.add(user.getId());
    return user;
  }
}
