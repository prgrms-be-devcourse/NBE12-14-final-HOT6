package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 가맹점 저장 트랜잭션이 점주 회원 행을 잠그고 활동 중인 점주인지 다시 확인하는지 본다. 테스트가 끝나면 롤백한다. */
@IntegrationTest
@Transactional
class MerchantRegisterOwnerLockTest {

  private static final MerchantRegisterRequest REQUEST =
      new MerchantRegisterRequest("잠금 가게", "인천광역시 남동구 구월로 1", null);

  @Autowired MerchantService merchantService;
  @Autowired UserRepository userRepository;
  @Autowired MerchantRepository merchantRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("활동 중인 점주는 가맹점이 등록된다")
  void 활동중인_점주는_등록() {
    Long ownerId = save("lock1@devpay.com", "01062000001", UserRole.OWNER).getId();

    merchantService.registerByOwner(ownerId, REQUEST, null);

    assertThat(merchantRepository.existsByOwnerId(ownerId)).isTrue();
  }

  @Test
  @DisplayName("탈퇴한 점주는 COM-009 이고 가맹점이 만들어지지 않는다")
  void 탈퇴한_점주는_거절() {
    Long ownerId = save("lock2@devpay.com", "01062000002", UserRole.OWNER).getId();
    jdbcTemplate.update("update users set status = 'WITHDRAWN' where id = ?", ownerId);

    assertErrorCode(() -> merchantService.registerByOwner(ownerId, REQUEST, null), CommonErrorCode.RESOURCE_NOT_FOUND);

    assertThat(merchantRepository.existsByOwnerId(ownerId)).isFalse();
  }

  @Test
  @DisplayName("없는 회원은 COM-009 이고 가맹점이 만들어지지 않는다")
  void 없는_회원은_거절() {
    assertErrorCode(() -> merchantService.registerByOwner(999_999L, REQUEST, null), CommonErrorCode.RESOURCE_NOT_FOUND);

    assertThat(merchantRepository.existsByOwnerId(999_999L)).isFalse();
  }

  @Test
  @DisplayName("점주가 아니면 COM-008 이고 가맹점이 만들어지지 않는다")
  void 점주가_아니면_거절() {
    Long userId = save("lock3@devpay.com", "01062000003", UserRole.USER).getId();

    assertErrorCode(() -> merchantService.registerByOwner(userId, REQUEST, null), CommonErrorCode.FORBIDDEN);

    assertThat(merchantRepository.existsByOwnerId(userId)).isFalse();
  }

  private User save(String email, String phone, UserRole role) {
    return userRepository.saveAndFlush(User.create("홍길동", email, phone, "pw-hash", "pin-hash", role));
  }

  private void assertErrorCode(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }
}
