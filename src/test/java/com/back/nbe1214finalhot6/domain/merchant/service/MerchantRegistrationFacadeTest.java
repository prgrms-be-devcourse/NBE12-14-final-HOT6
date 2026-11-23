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
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 가맹점 등록과 점주 승격이 한 트랜잭션으로 묶이는지 실제 DB 로 확인한다. */
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
          .findFirstByOwnerIdOrderByIdAsc(userId)
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

  // 목적: 가맹점 저장이 실패하면 승격도 함께 취소되는지 확인한다(함께 성공하거나 함께 취소).
  // 이름이 없는 요청으로 승격이 일어난 뒤 가맹점 저장이 실패하게 한다.
  @Test
  @DisplayName("가맹점 저장이 실패하면 승격도 취소되어 역할이 그대로다")
  void promotionRollsBackWhenSaveFails() {
    User user = save("a@devpay.com", "01011110001", UserRole.USER);
    MerchantRegisterRequest noName = new MerchantRegisterRequest(null, "인천광역시 남동구 1", null);

    assertThatThrownBy(() -> facade.register(authUser(user), noName))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(roleOf(user.getId())).isEqualTo("USER");
    assertThat(newMerchants()).isZero();
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
