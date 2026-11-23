package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.BudgetTransferResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetResponse;
import com.back.nbe1214finalhot6.domain.cashback.entity.BudgetTransferType;
import com.back.nbe1214finalhot6.domain.cashback.exception.CashbackErrorCode;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetTransferRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 월 예산 이월. 11월·12월 배정 각 1,000,000. 테스트 시계는 2026-11-18 09:00 KST 다. */
@IntegrationTest
@Transactional
class CashbackBudgetTransferServiceTest {

  private static final BigDecimal RATE = new BigDecimal("0.03");

  @Autowired private CashbackBudgetTransferService transferService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetTransferRepository transferRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @PersistenceContext private EntityManager entityManager;

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(2026, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(1_000_000L, RATE));
    budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(1_000_000L, RATE));
  }

  private BudgetTransferRequest request(int year, int month, long amount) {
    return new BudgetTransferRequest(year, month, amount);
  }

  private MonthlyBudgetResponse monthly(int year, int month) {
    entityManager.flush();
    entityManager.clear();
    return budgetService.getMonthly(year, month);
  }

  private AnnualBudgetResponse annual(int year) {
    entityManager.flush();
    entityManager.clear();
    return budgetService.getAnnual(year);
  }

  private void assertBusiness(Runnable action, CashbackErrorCode code) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", code);
  }

  // 목적: 같은 해 안의 이월이 원 월의 남은 예산을 줄이고(이월 유출) 다음 달의 남은 예산을 같은 금액만큼 늘리며(이월 유입), 이력(수동, 처리자 없음)이
  // 남고, 연 예산은 변하지 않는지 확인한다.
  @Test
  @DisplayName("이월하면 원 월은 줄고 다음 달은 늘며 이력이 남고 연 예산은 그대로다")
  void transfer_withinYear() {
    BudgetTransferResponse result = transferService.transfer(request(2026, 11, 300_000L));

    assertThat(result.fromYear()).isEqualTo(2026);
    assertThat(result.fromMonth()).isEqualTo(11);
    assertThat(result.toYear()).isEqualTo(2026);
    assertThat(result.toMonth()).isEqualTo(12);
    assertThat(result.amount()).isEqualTo(300_000L);
    assertThat(result.type()).isEqualTo(BudgetTransferType.MANUAL);
    assertThat(result.createdBy()).isNull();
    assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));

    MonthlyBudgetResponse november = monthly(2026, 11);
    assertThat(november.carriedOutAmount()).isEqualTo(300_000L);
    assertThat(november.remainingAmount()).isEqualTo(700_000L);
    MonthlyBudgetResponse december = monthly(2026, 12);
    assertThat(december.carriedInAmount()).isEqualTo(300_000L);
    assertThat(december.remainingAmount()).isEqualTo(1_300_000L);
    AnnualBudgetResponse annual = annual(2026);
    assertThat(annual.carriedInAmount()).isZero();
    assertThat(annual.carriedOutAmount()).isZero();
    assertThat(annual.remainingAmount()).isEqualTo(12_000_000L);
    assertThat(transferRepository.count()).isEqualTo(1);
  }

  // 목적: 남은 예산을 정확히 전부 넘길 수 있고(경계), 1원이라도 많으면 CSH-011 로 거절되며 아무것도 바뀌지 않는지 확인한다. 부분 이월 뒤의 남은
  // 예산 기준이기도 하다.
  @Test
  @DisplayName("남은 예산만큼은 넘기고 1원 많으면 거절하며 아무것도 바꾸지 않는다")
  void transfer_boundary() {
    transferService.transfer(request(2026, 11, 400_000L));

    assertBusiness(
        () -> transferService.transfer(request(2026, 11, 600_001L)),
        CashbackErrorCode.TRANSFER_EXCEEDS_REMAINING);
    assertThat(monthly(2026, 11).carriedOutAmount()).isEqualTo(400_000L);
    assertThat(monthly(2026, 12).carriedInAmount()).isEqualTo(400_000L);
    assertThat(transferRepository.count()).isEqualTo(1);

    transferService.transfer(request(2026, 11, 600_000L));

    assertThat(monthly(2026, 11).remainingAmount()).isZero();
    assertThat(monthly(2026, 12).remainingAmount()).isEqualTo(2_000_000L);
  }

  // 목적: 금액이 0 이하이거나 월이 1~12 가 아니면 CSH-007 로 거절되는지 확인한다.
  @Test
  @DisplayName("금액이 0 이하이거나 월이 올바르지 않으면 거절한다")
  void transfer_invalidInput() {
    assertBusiness(() -> transferService.transfer(request(2026, 11, 0L)), CashbackErrorCode.INVALID_BUDGET);
    assertBusiness(() -> transferService.transfer(request(2026, 11, -1L)), CashbackErrorCode.INVALID_BUDGET);
    assertBusiness(() -> transferService.transfer(request(2026, 13, 1L)), CashbackErrorCode.INVALID_BUDGET);
    assertBusiness(() -> transferService.transfer(request(2026, 0, 1L)), CashbackErrorCode.INVALID_BUDGET);
  }

  // 목적: 넘기는 달이나 받는 달의 월 예산이 없으면 CSH-004 로 거절되고 넘기는 달이 줄어들지 않는지 확인한다.
  @Test
  @DisplayName("넘기는 달이나 받는 달의 월 예산이 없으면 거절한다")
  void transfer_missingBudget() {
    assertBusiness(() -> transferService.transfer(request(2026, 3, 1_000L)), CashbackErrorCode.BUDGET_NOT_FOUND);

    // 12월을 넘기려면 다음 해 1월이 필요하다
    assertBusiness(() -> transferService.transfer(request(2026, 12, 1_000L)), CashbackErrorCode.BUDGET_NOT_FOUND);
    assertThat(monthly(2026, 12).carriedOutAmount()).isZero();
  }

  // 목적: 이월 후 예산 확보가 이월 유출을 반영하는지(원 월은 넘긴 만큼 덜 쓰고) 다음 달은 배정을 넘어 이월 유입만큼 더 확보할 수 있는지 확인한다.
  @Test
  @DisplayName("이월 후 확보는 원 월에서 줄어든 남은 예산과 다음 달에서 늘어난 남은 예산을 따른다")
  void transfer_affectsReservations() {
    transferService.transfer(request(2026, 11, 300_000L));

    assertThat(budgetService.reserveBudget(2026, 11, 700_001L)).isEqualTo(BudgetReserveResult.INSUFFICIENT);
    assertThat(budgetService.reserveBudget(2026, 11, 700_000L)).isEqualTo(BudgetReserveResult.RESERVED);
    assertThat(budgetService.reserveBudget(2026, 12, 1_300_000L)).isEqualTo(BudgetReserveResult.RESERVED);
  }

  // 목적: 적립 중단·마감 상태의 달도 이월할 수 있는지 확인한다.
  @Test
  @DisplayName("적립이 중단되거나 마감된 달도 이월할 수 있다")
  void transfer_ignoresStatus() {
    jdbcTemplate.update(
        "update cashback_monthly_budget set status = 'STOPPED' where budget_year = 2026 and budget_month = 11");
    transferService.transfer(request(2026, 11, 100_000L));
    jdbcTemplate.update(
        "update cashback_monthly_budget set status = 'CLOSED' where budget_year = 2026 and budget_month = 11");
    transferService.transfer(request(2026, 11, 100_000L));

    assertThat(monthly(2026, 11).carriedOutAmount()).isEqualTo(200_000L);
    assertThat(monthly(2026, 12).carriedInAmount()).isEqualTo(200_000L);
  }

  // 목적: 이월로 넘긴 금액이 있는 달은 월 배정을 바꿀 수 없는지(남은 예산이 음수가 될 수 있음) 확인한다. 이월을 받기만 한 달은 배정을 바꿀 수 있다.
  @Test
  @DisplayName("넘긴 금액이 있는 달은 월 배정을 바꿀 수 없다")
  void putMonthly_blockedAfterTransferOut() {
    transferService.transfer(request(2026, 11, 100_000L));
    // 이월은 컨텍스트를 비우지 않는 UPDATE 라서, 같은 트랜잭션에서 읽어 둔 낡은 엔티티를 버리고 다시 읽는다
    entityManager.flush();
    entityManager.clear();

    assertBusiness(
        () -> budgetService.putMonthly(2026, 11, new MonthlyBudgetRequest(900_000L, null)),
        CashbackErrorCode.BUDGET_IN_USE);
    assertThat(budgetService.putMonthly(2026, 12, new MonthlyBudgetRequest(900_000L, null)).allocatedAmount())
        .isEqualTo(900_000L);
  }

  // 목적: 12월 이월이 다음 해 1월로 들어가고 연 예산도 함께 맞춰져서(넘기는 해 이월 유출, 받는 해 이월 유입) 받은 금액을 연 예산 한도 때문에 못 쓰는 일이
  // 없는지 확인한다. 2027년 연 예산 총액이 1월 배정과 같아도 이월분까지 확보할 수 있어야 한다.
  @Test
  @DisplayName("12월 이월은 다음 해 1월로 들어가고 연 예산도 함께 맞춘다")
  void transfer_crossYear() {
    budgetService.putAnnual(2027, new AnnualBudgetRequest(1_000_000L));
    budgetService.putMonthly(2027, 1, new MonthlyBudgetRequest(1_000_000L, RATE));

    BudgetTransferResponse result = transferService.transfer(request(2026, 12, 200_000L));

    assertThat(result.toYear()).isEqualTo(2027);
    assertThat(result.toMonth()).isEqualTo(1);
    assertThat(monthly(2026, 12).remainingAmount()).isEqualTo(800_000L);
    assertThat(monthly(2027, 1).remainingAmount()).isEqualTo(1_200_000L);
    assertThat(annual(2026).carriedOutAmount()).isEqualTo(200_000L);
    assertThat(annual(2026).remainingAmount()).isEqualTo(11_800_000L);
    assertThat(annual(2027).carriedInAmount()).isEqualTo(200_000L);
    assertThat(annual(2027).remainingAmount()).isEqualTo(1_200_000L);
    assertThat(budgetService.reserveBudget(2027, 1, 1_200_000L)).isEqualTo(BudgetReserveResult.RESERVED);
    assertThat(budgetService.reserveBudget(2027, 1, 1L)).isNotEqualTo(BudgetReserveResult.RESERVED);
  }

  // 목적: 받는 해의 연 예산이나 1월 월 예산이 없으면 12월 이월이 거절되고 연 예산·월 예산 어느 쪽도 바뀌지 않는지 확인한다.
  @Test
  @DisplayName("받는 해의 연 예산이나 월 예산이 없으면 연말 이월을 거절한다")
  void transfer_crossYear_missingTarget() {
    assertBusiness(() -> transferService.transfer(request(2026, 12, 100_000L)), CashbackErrorCode.BUDGET_NOT_FOUND);

    budgetService.putAnnual(2027, new AnnualBudgetRequest(1_000_000L));
    assertBusiness(() -> transferService.transfer(request(2026, 12, 100_000L)), CashbackErrorCode.BUDGET_NOT_FOUND);

    assertThat(annual(2026).carriedOutAmount()).isZero();
    assertThat(annual(2027).carriedInAmount()).isZero();
    assertThat(monthly(2026, 12).carriedOutAmount()).isZero();
  }

  // 목적: 이월 이력이 연도별(넘긴 달 기준)로 최근순으로 나오고 연말 이월은 넘긴 해에 속하며 없는 해는 빈 목록인지 확인한다.
  @Test
  @DisplayName("이월 이력은 넘긴 달의 연도별로 최근순으로 나온다")
  void list_byYearNewestFirst() {
    budgetService.putAnnual(2027, new AnnualBudgetRequest(1_000_000L));
    budgetService.putMonthly(2027, 1, new MonthlyBudgetRequest(1_000_000L, RATE));
    transferService.transfer(request(2026, 11, 100_000L));
    transferService.transfer(request(2026, 12, 50_000L));
    transferService.transfer(request(2026, 11, 20_000L));

    List<BudgetTransferResponse> list = transferService.list(2026);

    assertThat(list).extracting(BudgetTransferResponse::amount).containsExactly(20_000L, 50_000L, 100_000L);
    assertThat(list.get(1).toYear()).isEqualTo(2027);
    assertThat(list.get(1).toMonth()).isEqualTo(1);
    assertThat(transferService.list(2027)).isEmpty();
    assertThat(transferService.list(2030)).isEmpty();
  }
}
