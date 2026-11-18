package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterResponse;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 DB(docker compose 의 PostgreSQL)가 켜져 있어야 한다.
@SpringBootTest
@Transactional
class MerchantServiceTest {

  @Autowired private MerchantService merchantService;
  @Autowired private MerchantRepository merchantRepository;

  // 목적: 등록한 값이 DB에 저장되고 다시 조회했을 때 요청값과 같은지 확인한다.
  @Test
  @DisplayName("가맹점을 등록하면 요청한 값으로 저장된다")
  void register_savesMerchant() {
    MerchantRegisterRequest request =
        new MerchantRegisterRequest(1L, "테스트 가맹점", "인천광역시 남동구 어딘가 1", null);

    MerchantRegisterResponse response = merchantService.register(request);

    Merchant saved = merchantRepository.findById(response.merchantId()).orElseThrow();
    assertThat(saved.getOwnerId()).isEqualTo(1L);
    assertThat(saved.getName()).isEqualTo("테스트 가맹점");
    assertThat(saved.getAddress()).isEqualTo("인천광역시 남동구 어딘가 1");
  }

  // 목적: cashbackEligible 을 생략(null)했을 때 기본값 true 가 적용되는지 확인한다.
  @Test
  @DisplayName("적립 대상 여부를 보내지 않으면 기본값은 true")
  void register_defaultCashbackEligible() {
    MerchantRegisterRequest request =
        new MerchantRegisterRequest(1L, "기본값 가맹점", "인천광역시 서구 어딘가 2", null);

    MerchantRegisterResponse response = merchantService.register(request);

    assertThat(response.cashbackEligible()).isTrue();
  }

  // 목적: cashbackEligible 을 false 로 명시하면 기본값에 덮이지 않고 false 로 저장되는지 확인한다.
  @Test
  @DisplayName("적립 대상 여부를 false 로 보내면 false 로 저장된다")
  void register_cashbackEligibleFalse() {
    MerchantRegisterRequest request =
        new MerchantRegisterRequest(1L, "비적립 가맹점", "인천광역시 부평구 어딘가 3", false);

    MerchantRegisterResponse response = merchantService.register(request);

    assertThat(response.cashbackEligible()).isFalse();
  }
}
