package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** 본인 가맹점 판정(#301). 이용자와 점주는 계정이 따로라 지갑 주인과 점주의 전화번호로 같은 사람인지 본다. */
@IntegrationTest
@Transactional
class MerchantSelfPaymentTest {

  private static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired private MerchantService merchantService;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private WalletRepository walletRepository;

  private Long user(String phone, UserRole role) {
    String email = "self-pay-" + SEQ.incrementAndGet() + "@devpay.test";
    return userRepository.save(User.create("테스트", email, phone, "pw", "pin", role)).getId();
  }

  // 이용자의 지갑 번호. 결제와 미리보기는 지갑 번호로 판정을 부른다
  private Long walletOf(Long userId) {
    return walletRepository.save(Wallet.create(userId, LocalDateTime.now())).getId();
  }

  private Long merchantOf(Long ownerId) {
    return merchantRepository.save(Merchant.create(ownerId, "본인 판정 가맹점", "서울", true)).getId();
  }

  // 목적: 같은 사람이 같은 전화번호로 이용자·점주 계정을 따로 가진 경우를 본인으로 판정하는지 확인한다.
  @Test
  @DisplayName("점주와 전화번호가 같은 이용자는 본인이다")
  void samePhoneIsSelf() {
    Long merchantId = merchantOf(user("01077770001", UserRole.OWNER));
    Long payerWallet = walletOf(user("01077770001", UserRole.USER));

    assertThat(merchantService.isOwnedBySamePerson(merchantId, payerWallet)).isTrue();
  }

  // 목적: 다른 사람(다른 전화번호)의 결제는 본인으로 보지 않는지 확인한다.
  @Test
  @DisplayName("전화번호가 다른 이용자는 본인이 아니다")
  void otherPhoneIsNotSelf() {
    Long merchantId = merchantOf(user("01077770002", UserRole.OWNER));
    Long payerWallet = walletOf(user("01077770003", UserRole.USER));

    assertThat(merchantService.isOwnedBySamePerson(merchantId, payerWallet)).isFalse();
  }

  // 목적: 점주가 없는 가맹점(테스트·이전 데이터)에서 조회가 실패하지 않고 본인이 아니라고 답하는지 확인한다.
  @Test
  @DisplayName("점주가 없는 가맹점은 본인이 아니다")
  void merchantWithoutOwnerIsNotSelf() {
    Long merchantId = merchantOf(null);
    Long payerWallet = walletOf(user("01077770004", UserRole.USER));

    assertThat(merchantService.isOwnedBySamePerson(merchantId, payerWallet)).isFalse();
  }
}
