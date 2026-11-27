package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** 가맹점 저장 중 DB 제약 위반을 어떻게 구분하는지. 어떤 제약을 어겼는지는 DB 가 알려 주는 제약 이름으로만 판단한다. */
@ExtendWith(MockitoExtension.class)
class MerchantServiceConstraintTest {

  private static final MerchantRegisterRequest REQUEST =
      new MerchantRegisterRequest("테스트 가맹점", "인천광역시 남동구 1", null);

  @Mock MerchantRepository merchantRepository;
  @InjectMocks MerchantService merchantService;

  private DataIntegrityViolationException violationOf(String constraintName, String message) {
    return new DataIntegrityViolationException(
        message,
        new ConstraintViolationException(message, new SQLException(message), constraintName));
  }

  // 목적: 점주당 하나 제약을 어기면(동시에 두 번 등록) 500 이 아니라 MER-002 로 응답하는지 확인한다.
  @Test
  @DisplayName("점주당 하나 제약을 어기면 MER-002 로 바꿔 던진다")
  void ownerUniqueViolationBecomesConflict() {
    given(merchantRepository.existsByOwnerId(1L)).willReturn(false);
    given(merchantRepository.saveAndFlush(any(Merchant.class)))
        .willThrow(violationOf(Merchant.OWNER_UNIQUE, "owner 중복"));

    assertThatThrownBy(() -> merchantService.register(1L, REQUEST))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MerchantErrorCode.MERCHANT_ALREADY_REGISTERED));
  }

  // 목적: 다른 제약 위반을 점주 중복으로 잘못 포장하면 진짜 원인이 가려지므로, 그대로 던지는지 확인한다.
  // 에러 메시지에 제약 이름이 들어 있어도 DB 가 알려 준 제약 이름이 다르면 점주 중복이 아니다(글자 검색으로 판단하지 않는다).
  @Test
  @DisplayName("다른 제약 위반은 MER-002 로 바꾸지 않고 그대로 던진다")
  void otherViolationIsRethrown() {
    given(merchantRepository.existsByOwnerId(1L)).willReturn(false);
    DataIntegrityViolationException other =
        violationOf("merchant_name_not_null", "메시지에 " + Merchant.OWNER_UNIQUE + " 가 들어 있어도");
    given(merchantRepository.saveAndFlush(any(Merchant.class))).willThrow(other);

    assertThatThrownBy(() -> merchantService.register(1L, REQUEST)).isSameAs(other);
  }
}
