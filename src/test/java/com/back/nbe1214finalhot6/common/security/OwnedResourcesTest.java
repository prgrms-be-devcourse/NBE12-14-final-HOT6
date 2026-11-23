package com.back.nbe1214finalhot6.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.security.AuthUser;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/** 토큰의 회원으로 내 지갑·가맹점 번호를 찾는지. 번호를 요청으로 받지 않아도 되게 하는 공용 도구다. */
@IntegrationTest
class OwnedResourcesTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);
  private static final Long USER_ID = 9_701L;
  private static final Long OTHER_USER_ID = 9_702L;

  @Autowired OwnedResources ownedResources;
  @Autowired WalletRepository walletRepository;
  @Autowired MerchantRepository merchantRepository;

  // 이 테스트가 만든 행만 지운다(다른 테스트의 데이터를 건드리지 않음)
  private final List<Long> walletIds = new ArrayList<>();
  private final List<Long> merchantIds = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    walletRepository.deleteAllById(walletIds);
    merchantRepository.deleteAllById(merchantIds);
  }

  private AuthUser user(Long userId, UserRole role) {
    return new AuthUser(userId, role);
  }

  private Long saveWallet(Long userId) {
    Long id = walletRepository.save(Wallet.create(userId, NOW)).getId();
    walletIds.add(id);
    return id;
  }

  private Long saveMerchant(Long ownerId, String name) {
    Long id = merchantRepository.save(Merchant.create(ownerId, name, "서울", true)).getId();
    merchantIds.add(id);
    return id;
  }

  private void assertFails(Runnable call, Enum<?> expected) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  // 목적: 토큰의 회원 번호로 그 회원의 지갑 번호를 찾는지 확인한다.
  @Test
  @DisplayName("내 지갑 번호를 돌려준다")
  void walletIdOf() {
    Long mine = saveWallet(USER_ID);

    assertThat(ownedResources.walletIdOf(user(USER_ID, UserRole.USER))).isEqualTo(mine);
  }

  // 목적: 다른 회원의 지갑이 있어도 내 번호로는 내 지갑만 나와야 남의 지갑을 쓸 수 없다.
  @Test
  @DisplayName("다른 회원의 지갑은 돌려주지 않는다")
  void walletIdOf_notOthers() {
    Long mine = saveWallet(USER_ID);
    Long others = saveWallet(OTHER_USER_ID);

    assertThat(ownedResources.walletIdOf(user(USER_ID, UserRole.USER)))
        .isEqualTo(mine)
        .isNotEqualTo(others);
    assertThat(ownedResources.walletIdOf(user(OTHER_USER_ID, UserRole.USER))).isEqualTo(others);
  }

  // 목적: 지갑이 없으면 404(WAL-001)로 응답한다. 번호를 요청으로 받지 않으므로 "없음"만 남는다.
  @Test
  @DisplayName("지갑이 없으면 WAL-001 로 거절한다")
  void walletIdOf_notFound() {
    assertFails(
        () -> ownedResources.walletIdOf(user(USER_ID, UserRole.USER)),
        WalletErrorCode.WALLET_NOT_FOUND);
    assertThat(WalletErrorCode.WALLET_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // 목적: 토큰의 회원(점주)이 등록한 가맹점 번호를 찾는지 확인한다.
  @Test
  @DisplayName("내 가맹점 번호를 돌려준다")
  void merchantIdOf() {
    Long mine = saveMerchant(USER_ID, "내 가게");
    saveMerchant(OTHER_USER_ID, "남의 가게");

    assertThat(ownedResources.merchantIdOf(user(USER_ID, UserRole.OWNER))).isEqualTo(mine);
  }

  // 목적: 점주당 1개를 막는 제약이 생기기 전까지, 여러 개여도 가장 먼저 등록한 가맹점으로 항상 같은 값을 준다.
  @Test
  @DisplayName("가맹점이 여러 개면 가장 먼저 등록한 것을 돌려준다")
  void merchantIdOf_earliest() {
    Long first = saveMerchant(USER_ID, "첫 가게");
    saveMerchant(USER_ID, "둘째 가게");

    assertThat(ownedResources.merchantIdOf(user(USER_ID, UserRole.OWNER))).isEqualTo(first);
  }

  // 목적: 가맹점이 없으면 404(MER-001)로 응답한다.
  @Test
  @DisplayName("가맹점이 없으면 MER-001 로 거절한다")
  void merchantIdOf_notFound() {
    saveMerchant(OTHER_USER_ID, "남의 가게");

    assertFails(
        () -> ownedResources.merchantIdOf(user(USER_ID, UserRole.OWNER)),
        MerchantErrorCode.MERCHANT_NOT_FOUND);
    assertThat(MerchantErrorCode.MERCHANT_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
