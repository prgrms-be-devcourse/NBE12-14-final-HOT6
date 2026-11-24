package com.back.nbe1214finalhot6.domain.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.wallet.dto.WalletTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 지갑 거래 내역 서비스 테스트 (이슈 #169). */
@IntegrationTest
class WalletTransactionServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 9, 0);

  @Autowired WalletTransactionService walletTransactionService;
  @Autowired WalletRepository walletRepository;
  @Autowired WalletTransactionRepository walletTransactionRepository;

  // 테스트가 DB에 남긴 행 정리(원장 → 지갑 순)
  @AfterEach
  void cleanUp() {
    walletTransactionRepository.deleteAll();
    walletRepository.deleteAll();
  }

  @Test
  @DisplayName("내 지갑의 거래만 조회되고 다른 지갑의 거래는 섞이지 않는다")
  void 내_지갑만() {
    // given : 지갑 2개에 각각 원장 행을 남긴다
    Wallet mine = wallet(9_701L);
    Wallet other = wallet(9_702L);
    WalletTransaction mine1 = ledger(mine, WalletTransactionType.CHARGE, 10_000L, 10_000L, null, NOW);
    WalletTransaction mine2 =
        ledger(mine, WalletTransactionType.PAYMENT_DEVMONEY, -3_000L, 7_000L, 1L, NOW.plusMinutes(1));
    ledger(other, WalletTransactionType.CHARGE, 50_000L, 50_000L, null, NOW);

    // when
    PageResponse<WalletTransactionResponse> page =
        walletTransactionService.getTransactions(mine.getId(), 0, 10);

    // then : 내 지갑의 2건만 있고 다른 지갑의 거래는 없다
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
        .extracting(WalletTransactionResponse::transactionId)
        .containsExactlyInAnyOrder(mine1.getId(), mine2.getId());
  }

  @Test
  @DisplayName("최신순으로 정렬되고, 같은 시각이면 나중에 남은 행이 먼저 온다")
  void 최신순() {
    // given : 일부러 오래된 시각을 나중에 저장하고, 같은 시각의 행 2개를 순서대로 저장한다
    Wallet wallet = wallet(9_703L);
    WalletTransaction at11 = ledger(wallet, WalletTransactionType.CHARGE, 100L, 100L, null, NOW.plusHours(2));
    WalletTransaction at09 = ledger(wallet, WalletTransactionType.REFUND, -100L, 0L, 1L, NOW);
    WalletTransaction at10 = ledger(wallet, WalletTransactionType.CHARGE, 100L, 100L, 2L, NOW.plusHours(1));
    WalletTransaction sameFirst =
        ledger(wallet, WalletTransactionType.PAYMENT_DEVMONEY, -10L, 90L, 3L, NOW.plusHours(3));
    WalletTransaction sameSecond =
        ledger(wallet, WalletTransactionType.PAYMENT_CASHBACK, -20L, 80L, 4L, NOW.plusHours(3));

    // when
    List<WalletTransactionResponse> content =
        walletTransactionService.getTransactions(wallet.getId(), 0, 10).content();

    // then : 시각 내림차순, 같은 시각(12:00)이면 나중에 저장한 것이 먼저
    assertThat(content)
        .extracting(WalletTransactionResponse::transactionId)
        .containsExactly(
            sameSecond.getId(), sameFirst.getId(), at11.getId(), at10.getId(), at09.getId());
  }

  @Test
  @DisplayName("페이지 크기만큼 나누고 마지막 페이지와 전체 건수를 알려 준다")
  void 페이징() {
    // given : 원장 행 25개
    Wallet wallet = wallet(9_704L);
    for (int i = 0; i < 25; i++) {
      ledger(wallet, WalletTransactionType.CHARGE, 100L, 100L * (i + 1), null, NOW.plusMinutes(i));
    }

    // when
    PageResponse<WalletTransactionResponse> first = walletTransactionService.getTransactions(wallet.getId(), 0, 10);
    PageResponse<WalletTransactionResponse> last = walletTransactionService.getTransactions(wallet.getId(), 2, 10);

    // then : 10건씩 3페이지, 마지막은 5건
    assertThat(first.content()).hasSize(10);
    assertThat(last.content()).hasSize(5);
    assertThat(last.page()).isEqualTo(2);
    assertThat(first.totalElements()).isEqualTo(25);
    assertThat(first.totalPages()).isEqualTo(3);
  }

  @Test
  @DisplayName("size 가 100을 넘으면 100으로 줄이고, 거래가 없으면 빈 목록이다")
  void 크기상한_빈목록() {
    // given : 원장 행이 없는 지갑
    Wallet wallet = wallet(9_705L);

    // when
    PageResponse<WalletTransactionResponse> page =
        walletTransactionService.getTransactions(wallet.getId(), 0, 1_000);

    // then
    assertThat(page.content()).isEmpty();
    assertThat(page.size()).isEqualTo(100);
    assertThat(page.totalElements()).isZero();
  }

  @Test
  @DisplayName("거래 종류별로 부호 있는 금액과 거래 후 잔액, 캐시백 구분이 맞다")
  void 항목_매핑() {
    // given
    Wallet wallet = wallet(9_706L);
    ledger(wallet, WalletTransactionType.CHARGE, 10_000L, 10_000L, null, NOW);
    ledger(wallet, WalletTransactionType.PAYMENT_DEVMONEY, -7_000L, 3_000L, 1L, NOW.plusMinutes(1));
    ledger(wallet, WalletTransactionType.PAYMENT_CASHBACK, -500L, 2_500L, 1L, NOW.plusMinutes(2));
    ledger(wallet, WalletTransactionType.CASHBACK_EARN, 210L, 2_710L, 7L, NOW.plusMinutes(3));

    // when
    Map<WalletTransactionType, WalletTransactionResponse> byType =
        walletTransactionService.getTransactions(wallet.getId(), 0, 10).content().stream()
            .collect(Collectors.toMap(WalletTransactionResponse::type, Function.identity()));

    // then : 입금은 +, 차감은 -, 잔액은 원장 값, 캐시백 잔액을 바꾼 거래만 cashback=true
    assertThat(byType.get(WalletTransactionType.CHARGE).amount()).isEqualTo(10_000L);
    assertThat(byType.get(WalletTransactionType.CHARGE).cashback()).isFalse();
    assertThat(byType.get(WalletTransactionType.PAYMENT_DEVMONEY).amount()).isEqualTo(-7_000L);
    assertThat(byType.get(WalletTransactionType.PAYMENT_DEVMONEY).balanceAfter()).isEqualTo(3_000L);
    assertThat(byType.get(WalletTransactionType.PAYMENT_CASHBACK).amount()).isEqualTo(-500L);
    assertThat(byType.get(WalletTransactionType.PAYMENT_CASHBACK).cashback()).isTrue();
    assertThat(byType.get(WalletTransactionType.CASHBACK_EARN).amount()).isEqualTo(210L);
    assertThat(byType.get(WalletTransactionType.CASHBACK_EARN).balanceAfter()).isEqualTo(2_710L);
    assertThat(byType.get(WalletTransactionType.CASHBACK_EARN).cashback()).isTrue();
  }

  @Test
  @DisplayName("결제 거래는 paymentId 가 refKey 와 같고, 캐시백 적립·회수와 충전은 paymentId 가 null 이다")
  void 참조키_결제ID() {
    // given : 결제 관련 거래는 refKey 가 결제 ID, 캐시백 적립·회수는 적립 건 ID, 충전은 refKey 없음
    Wallet wallet = wallet(9_707L);
    ledger(wallet, WalletTransactionType.PAYMENT_DEVMONEY, -7_000L, 3_000L, 55L, NOW);
    ledger(wallet, WalletTransactionType.PAYMENT_CANCEL_CASHBACK, 500L, 3_500L, 55L, NOW.plusMinutes(1));
    ledger(wallet, WalletTransactionType.CASHBACK_EARN, 210L, 3_710L, 7L, NOW.plusMinutes(2));
    ledger(wallet, WalletTransactionType.CASHBACK_CLAWBACK, -210L, 3_500L, 7L, NOW.plusMinutes(3));
    ledger(wallet, WalletTransactionType.CHARGE, 10_000L, 13_500L, null, NOW.plusMinutes(4));

    // when
    Map<WalletTransactionType, WalletTransactionResponse> byType =
        walletTransactionService.getTransactions(wallet.getId(), 0, 10).content().stream()
            .collect(Collectors.toMap(WalletTransactionResponse::type, Function.identity()));

    // then
    assertThat(byType.get(WalletTransactionType.PAYMENT_DEVMONEY).refKey()).isEqualTo(55L);
    assertThat(byType.get(WalletTransactionType.PAYMENT_DEVMONEY).paymentId()).isEqualTo(55L);
    assertThat(byType.get(WalletTransactionType.PAYMENT_CANCEL_CASHBACK).paymentId()).isEqualTo(55L);
    // 적립·회수의 refKey 는 적립 건 ID 라서 결제 ID 로 내리지 않는다
    assertThat(byType.get(WalletTransactionType.CASHBACK_EARN).refKey()).isEqualTo(7L);
    assertThat(byType.get(WalletTransactionType.CASHBACK_EARN).paymentId()).isNull();
    assertThat(byType.get(WalletTransactionType.CASHBACK_CLAWBACK).refKey()).isEqualTo(7L);
    assertThat(byType.get(WalletTransactionType.CASHBACK_CLAWBACK).paymentId()).isNull();
    assertThat(byType.get(WalletTransactionType.CHARGE).refKey()).isNull();
    assertThat(byType.get(WalletTransactionType.CHARGE).paymentId()).isNull();
  }

  private Wallet wallet(Long userId) {
    return walletRepository.save(Wallet.create(userId, NOW));
  }

  // 원장 행 하나. 읽기 테스트라 지갑 잔액과 맞추지 않고 값만 채운다
  private WalletTransaction ledger(
      Wallet wallet,
      WalletTransactionType type,
      long amount,
      long balanceAfter,
      Long refKey,
      LocalDateTime at) {
    return walletTransactionRepository.save(
        WalletTransaction.builder()
            .walletId(wallet.getId())
            .type(type)
            .amount(amount)
            .balanceAfter(balanceAfter)
            .refKey(refKey)
            .createdAt(at)
            .build());
  }
}
