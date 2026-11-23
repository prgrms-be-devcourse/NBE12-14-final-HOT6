package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackNotice;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackPolicy;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 같은 정책에 같은 종류의 공지가 두 번 생기지 않는지 확인한다. 유니크 제약 위반은 실제 INSERT 가 나가야 확인되고 트랜잭션을 망가뜨리므로 테스트 트랜잭션으로
 * 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackNoticeUniqueTest {

  @Autowired private CashbackNoticeRepository noticeRepository;

  @AfterEach
  void cleanUp() {
    noticeRepository.deleteAll();
  }

  // 목적: 같은 정책 ID 로 정책 변경 예고를 한 번 더 저장하면 유니크 제약으로 거절되고 공지는 한 건만 남는지 확인한다.
  @Test
  @DisplayName("같은 정책의 정책 변경 예고는 한 번만 저장된다")
  void duplicatePolicyChange_rejected() {
    LocalDateTime now = LocalDateTime.of(2026, 11, 18, 9, 0);
    CashbackPolicy policy =
        CashbackPolicy.create(new BigDecimal("0.02"), now.plusDays(2), now.plusDays(12), now);
    ReflectionTestUtils.setField(policy, "id", 42L);
    noticeRepository.saveAndFlush(CashbackNotice.policyChange(policy, now));

    assertThatThrownBy(() -> noticeRepository.saveAndFlush(CashbackNotice.policyChange(policy, now)))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(noticeRepository.count()).isEqualTo(1);
  }
}
