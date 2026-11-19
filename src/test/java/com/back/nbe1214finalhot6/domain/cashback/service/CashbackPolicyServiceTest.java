package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyCreateRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.PolicyResponse;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackPolicyRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class CashbackPolicyServiceTest {

  private static final LocalDateTime NOV_1 = LocalDateTime.of(2027, 11, 1, 0, 0);
  private static final LocalDateTime DEC_1 = LocalDateTime.of(2027, 12, 1, 0, 0);
  private static final LocalDateTime JAN_1 = LocalDateTime.of(2028, 1, 1, 0, 0);
  private static final LocalDateTime OCT_1 = LocalDateTime.of(2027, 10, 1, 0, 0);

  @Autowired private CashbackPolicyService policyService;
  @Autowired private CashbackPolicyRepository policyRepository;

  private PolicyResponse register(String rate, LocalDateTime startsAt, LocalDateTime endsAt) {
    return policyService.create(new PolicyCreateRequest(new BigDecimal(rate), startsAt, endsAt));
  }

  private void assertOverlap(LocalDateTime startsAt, LocalDateTime endsAt) {
    assertThatThrownBy(() -> register("0.05", startsAt, endsAt))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.POLICY_PERIOD_OVERLAP);
  }

  // 목적: 시작이 과거인 정책은 등록되지 않고 저장도 되지 않는지 확인한다(테스트 시계는 2026-11-18 09:00 KST).
  @Test
  @DisplayName("시작 시각이 과거면 거부하고 저장하지 않는다")
  void create_pastStartRejected() {
    assertThatThrownBy(() -> register("0.03", LocalDateTime.of(2026, 11, 17, 0, 0), null))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.INVALID_POLICY);

    assertThat(policyService.findAll()).isEmpty();
  }

  // 목적: 등록한 값이 DB에 저장되고, 요율이 소수 넷째 자리로 정규화되는지 확인한다.
  @Test
  @DisplayName("정책을 등록하면 요청한 값으로 저장된다")
  void create_saves() {
    PolicyResponse response = register("0.03", NOV_1, DEC_1);

    assertThat(response.rate().toPlainString()).isEqualTo("0.0300");
    assertThat(policyRepository.findById(response.policyId())).isPresent();
    assertThat(response.startsAt()).isEqualTo(NOV_1);
    assertThat(response.endsAt()).isEqualTo(DEC_1);
  }

  // 목적: 기간이 일부 겹치는 경우(앞쪽, 뒤쪽 걸침)가 거부되는지 확인한다.
  @Test
  @DisplayName("기간이 일부 겹치면 거부한다")
  void create_partialOverlapRejected() {
    register("0.03", NOV_1, DEC_1);

    assertOverlap(LocalDateTime.of(2027, 11, 15, 0, 0), JAN_1);
    assertOverlap(OCT_1, LocalDateTime.of(2027, 11, 15, 0, 0));
  }

  // 목적: 기존 정책을 감싸거나 기존 정책 안에 포함되는 기간이 거부되는지 확인한다.
  @Test
  @DisplayName("기간이 기존 정책을 감싸거나 그 안에 있으면 거부한다")
  void create_containmentRejected() {
    register("0.03", NOV_1, DEC_1);

    assertOverlap(OCT_1, JAN_1);
    assertOverlap(LocalDateTime.of(2027, 11, 10, 0, 0), LocalDateTime.of(2027, 11, 20, 0, 0));
  }

  // 목적: 반개구간이라 종료 = 다음 시작이면 겹치지 않는 것으로 처리되는지 확인한다.
  @Test
  @DisplayName("경계가 맞닿는 기간은 허용한다")
  void create_adjacentAllowed() {
    register("0.03", NOV_1, DEC_1);

    register("0.05", DEC_1, JAN_1);
    register("0.02", OCT_1, NOV_1);

    assertThat(policyService.findAll()).hasSize(3);
  }

  // 목적: 무기한 정책이 있으면 그 이후 시작하는 모든 기간이 겹침으로 처리되는지 확인한다.
  @Test
  @DisplayName("무기한 정책과는 이후 기간이 모두 겹친다")
  void create_openEndedExisting() {
    register("0.03", NOV_1, null);

    assertOverlap(DEC_1, JAN_1);
    assertOverlap(DEC_1, null);
    register("0.02", OCT_1, NOV_1);
  }

  // 목적: 새 정책이 무기한이면 기존 정책의 시작이 그 이후인 경우 겹침으로 처리되는지 확인한다.
  @Test
  @DisplayName("새 정책이 무기한이면 이후에 시작하는 기존 정책과 겹친다")
  void create_openEndedNew() {
    register("0.03", DEC_1, JAN_1);

    assertOverlap(NOV_1, null);
    register("0.02", JAN_1, null);
  }

  // 목적: 겹침으로 거부된 요청은 저장되지 않는지 확인한다.
  @Test
  @DisplayName("거부된 정책은 저장되지 않는다")
  void create_rejectedNotSaved() {
    register("0.03", NOV_1, DEC_1);
    assertOverlap(NOV_1, DEC_1);

    assertThat(policyRepository.count()).isEqualTo(1);
  }

  // 목적: 없는 ID 를 조회하면 정책 없음 에러가 나는지 확인한다.
  @Test
  @DisplayName("없는 정책을 조회하면 CSH-001")
  void get_notFound() {
    assertThatThrownBy(() -> policyService.get(999_999L))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", CashbackErrorCode.POLICY_NOT_FOUND);
  }

  // 목적: 목록이 시작이 늦은 순으로 나오는지 확인한다.
  @Test
  @DisplayName("목록은 시작이 늦은 순이다")
  void findAll_orderedByStartsAtDesc() {
    register("0.03", NOV_1, DEC_1);
    register("0.05", DEC_1, JAN_1);
    register("0.02", OCT_1, NOV_1);

    assertThat(policyService.findAll())
        .extracting(PolicyResponse::startsAt)
        .containsExactly(DEC_1, NOV_1, OCT_1);
  }

  // 목적: 시작 시각은 포함, 종료 시각은 제외로 유효 정책이 결정되는지 확인한다(경계 시각 오적용 방지).
  @Test
  @DisplayName("유효 정책 조회는 시작 포함, 종료 제외다")
  void findEffective_boundaries() {
    PolicyResponse nov = register("0.03", NOV_1, DEC_1);
    PolicyResponse dec = register("0.05", DEC_1, null);

    assertThat(policyService.findEffective(NOV_1.minusSeconds(1))).isEmpty();
    assertThat(policyService.findEffective(NOV_1)).get().isEqualTo(nov);
    assertThat(policyService.findEffective(DEC_1.minusSeconds(1))).get().isEqualTo(nov);
    assertThat(policyService.findEffective(DEC_1)).get().isEqualTo(dec);
    assertThat(policyService.findEffective(JAN_1.plusYears(50))).get().isEqualTo(dec);
  }
}
