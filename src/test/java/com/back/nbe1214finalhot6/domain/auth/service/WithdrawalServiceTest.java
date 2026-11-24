package com.back.nbe1214finalhot6.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.common.response.ErrorCode;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalPreviewResponse;
import com.back.nbe1214finalhot6.domain.auth.dto.WithdrawalResult;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackEarningRepository;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackService;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴. 환불·원장·상태 변경이 한 트랜잭션으로 처리되는지, 금액이 달라지면 거절하는지, 중간에 실패하면 모두 되돌려지는지를 실제 DB 로 확인한다.
 *
 * <p>캐시백 취소(CashbackService)만 가짜로 두고 호출 여부를 확인한다(예산·한도 처리는 캐시백 쪽 테스트가 확인한다). 롤백과 동시 요청을 보려면 실제로 커밋돼야 해서 테스트
 * 전체를 트랜잭션으로 감싸지 않고, 만든 행만 직접 지운다.
 */
@IntegrationTest
class WithdrawalServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 9, 0);
  private static final long FUNDED = 300_000L; // 시작할 때 지갑의 데브머니

  @Autowired WithdrawalService withdrawalService;
  @Autowired UserRepository userRepository;
  @Autowired WalletRepository walletRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired CashbackEarningRepository earningRepository;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired PlatformTransactionManager txManager;
  @MockitoBean CashbackService cashbackService;

  private final List<Member> members = new ArrayList<>();

  /** 테스트용 회원(회원 + 지갑 + 모의계좌). */
  private record Member(Long userId, Long walletId, Long accountId, long accountStart) {}

  @AfterEach
  void cleanUp() {
    for (Member m : members) {
      jdbcTemplate.update("delete from cashback_earning where wallet_id = ?", m.walletId());
      jdbcTemplate.update("delete from wallet_transaction where wallet_id = ?", m.walletId());
      jdbcTemplate.update("delete from account_transaction where account_id = ?", m.accountId());
      jdbcTemplate.update("delete from refund where wallet_id = ?", m.walletId());
      jdbcTemplate.update("delete from wallet where id = ?", m.walletId());
      jdbcTemplate.update("delete from account where id = ?", m.accountId());
      jdbcTemplate.update("delete from users where id = ?", m.userId());
    }
    members.clear();
  }

  private Member newMember(String tag, UserRole role, long devMoney) {
    User user =
        userRepository.save(
            User.create("홍길동", tag + "@devpay.com", phoneOf(tag), "pw", "pin", role));
    Wallet wallet = Wallet.create(user.getId(), NOW);
    if (devMoney > 0) {
      wallet.credit(devMoney, false, NOW);
    }
    Long walletId = walletRepository.save(wallet).getId();
    Account account = accountRepository.save(Account.create(user.getId(), NOW));
    Member member = new Member(user.getId(), walletId, account.getId(), account.getBalance());
    members.add(member);
    return member;
  }

  // 전화번호는 11자리이고 회원마다 달라야 한다
  private String phoneOf(String tag) {
    return "0107" + String.format("%07d", Math.abs(tag.hashCode()) % 10_000_000);
  }

  private Long pending(Member m, long paymentId, long amount) {
    CashbackEarning earning =
        CashbackEarning.pending(
            paymentId,
            m.walletId(),
            10_000L,
            NOW,
            new BigDecimal("0.0500"),
            1L,
            amount,
            NOW.plusDays(3),
            LocalDate.of(2026, 11, 1),
            NOW);
    return earningRepository.save(earning).getId();
  }

  private String userStatus(Member m) {
    return jdbcTemplate.queryForObject(
        "select status from users where id = ?", String.class, m.userId());
  }

  private String walletStatus(Member m) {
    return jdbcTemplate.queryForObject(
        "select status from wallet where id = ?", String.class, m.walletId());
  }

  private long walletBalance(Member m) {
    return jdbcTemplate.queryForObject(
        "select balance from wallet where id = ?", Long.class, m.walletId());
  }

  private long accountBalance(Member m) {
    return jdbcTemplate.queryForObject(
        "select balance from account where id = ?", Long.class, m.accountId());
  }

  private int count(String table, String column, Long id) {
    return jdbcTemplate.queryForObject(
        "select count(*) from " + table + " where " + column + " = ?", Integer.class, id);
  }

  private void assertFails(Runnable action, ErrorCode expected) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
  }

  private String key() {
    return UUID.randomUUID().toString();
  }

  // 목적: 정상 탈퇴. 남은 데브머니가 모의계좌로 환불되고(원장 포함), 지갑·회원이 탈퇴 상태가 되며,
  // 적립 예정 건만 소멸 처리(취소 호출)되고 이미 지급된 건은 건드리지 않는지 확인한다.
  @Test
  @DisplayName("탈퇴하면 데브머니가 모의계좌로 환불되고 지갑·회원이 탈퇴 상태가 된다")
  void settle() {
    Member m = newMember("wd1", UserRole.USER, FUNDED);
    pending(m, 9_801L, 700L);
    pending(m, 9_802L, 800L);
    Long paidId = pending(m, 9_803L, 900L);
    jdbcTemplate.update("update cashback_earning set status = 'PAID' where id = ?", paidId);

    WithdrawalResult result = withdrawalService.settle(m.userId(), m.walletId(), FUNDED, key());

    assertThat(result.refundedAmount()).isEqualTo(FUNDED);
    assertThat(result.canceledEarningCount()).isEqualTo(2);
    assertThat(result.canceledEarningAmount()).isEqualTo(1_500L);
    assertThat(walletBalance(m)).isZero();
    assertThat(accountBalance(m)).isEqualTo(m.accountStart() + FUNDED);
    assertThat(walletStatus(m)).isEqualTo("WITHDRAWN");
    assertThat(userStatus(m)).isEqualTo("WITHDRAWN");
    assertThat(jdbcTemplate.queryForObject("select withdrawn_at is not null from users where id = ?", Boolean.class, m.userId())).isTrue();
    assertThat(count("refund", "wallet_id", m.walletId())).isEqualTo(1);
    assertThat(count("wallet_transaction", "wallet_id", m.walletId())).isEqualTo(1); // REFUND 원장
    assertThat(count("account_transaction", "account_id", m.accountId())).isEqualTo(1);
    // 적립 예정(PENDING) 두 건만 취소를 부르고, 이미 지급된 건(9803)은 부르지 않는다
    verify(cashbackService).cancel(9_801L);
    verify(cashbackService).cancel(9_802L);
    verify(cashbackService, never()).cancel(9_803L);
  }

  // 목적: 미리보기에서 확인한 금액과 실행 시점의 금액이 다르면 거절하고, 아무것도 바꾸지 않는지 확인한다.
  @Test
  @DisplayName("확인받은 금액과 지금 금액이 다르면 AUTH-008 이고 아무것도 바뀌지 않는다")
  void amountChanged() {
    Member m = newMember("wd2", UserRole.USER, FUNDED);
    pending(m, 9_811L, 700L);

    assertThatThrownBy(() -> withdrawalService.settle(m.userId(), m.walletId(), FUNDED - 1, key()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.WITHDRAW_AMOUNT_CHANGED);
              assertThat(e.getMessage()).contains("300000원");
            });

    assertThat(walletBalance(m)).isEqualTo(FUNDED);
    assertThat(walletStatus(m)).isEqualTo("ACTIVE");
    assertThat(userStatus(m)).isEqualTo("ACTIVE");
    assertThat(accountBalance(m)).isEqualTo(m.accountStart());
    assertThat(count("refund", "wallet_id", m.walletId())).isZero();
    verify(cashbackService, never()).cancel(anyLong());
  }

  // 목적: 중간(캐시백 취소)에서 실패하면 이미 처리한 환불·상태 변경까지 모두 되돌려지는지 확인한다.
  @Test
  @DisplayName("중간에 실패하면 환불·상태 변경이 모두 롤백된다")
  void rollbackOnFailure() {
    Member m = newMember("wd3", UserRole.USER, FUNDED);
    pending(m, 9_821L, 700L);
    pending(m, 9_822L, 800L);
    willThrow(new IllegalStateException("취소 중 실패")).given(cashbackService).cancel(9_822L);

    assertThatThrownBy(() -> withdrawalService.settle(m.userId(), m.walletId(), FUNDED, key()))
        .isInstanceOf(IllegalStateException.class);

    assertThat(walletBalance(m)).isEqualTo(FUNDED);
    assertThat(accountBalance(m)).isEqualTo(m.accountStart());
    assertThat(walletStatus(m)).isEqualTo("ACTIVE");
    assertThat(userStatus(m)).isEqualTo("ACTIVE");
    assertThat(count("refund", "wallet_id", m.walletId())).isZero();
    assertThat(count("wallet_transaction", "wallet_id", m.walletId())).isZero();
    assertThat(count("account_transaction", "account_id", m.accountId())).isZero();
  }

  @Test
  @DisplayName("데브머니가 0원이어도 탈퇴할 수 있고 환불은 하지 않는다")
  void settleWithoutBalance() {
    Member m = newMember("wd4", UserRole.USER, 0);

    WithdrawalResult result = withdrawalService.settle(m.userId(), m.walletId(), 0, key());

    assertThat(result.refundedAmount()).isZero();
    assertThat(count("refund", "wallet_id", m.walletId())).isZero();
    assertThat(walletStatus(m)).isEqualTo("WITHDRAWN");
    assertThat(userStatus(m)).isEqualTo("WITHDRAWN");
  }

  // 목적: 음수 캐시백은 정산하지 않고 감수하는지(탈퇴할 수 있고, 캐시백 잔액은 그대로이며 데브머니로 상계하지 않는지) 확인한다.
  @Test
  @DisplayName("음수 캐시백이 있어도 탈퇴할 수 있고 정산하지 않는다")
  void negativeCashbackIsAccepted() {
    Member m = newMember("wd5", UserRole.USER, FUNDED);
    jdbcTemplate.update("update wallet set cashback_balance = -200 where id = ?", m.walletId());

    WithdrawalResult result = withdrawalService.settle(m.userId(), m.walletId(), FUNDED, key());

    assertThat(result.refundedAmount()).isEqualTo(FUNDED); // 데브머니는 음수 캐시백으로 깎이지 않고 전부 환불
    assertThat(
            jdbcTemplate.queryForObject(
                "select cashback_balance from wallet where id = ?", Long.class, m.walletId()))
        .isEqualTo(-200L);
    assertThat(userStatus(m)).isEqualTo("WITHDRAWN");
  }

  @Test
  @DisplayName("점주와 운영자는 탈퇴할 수 없다(AUTH-007)")
  void ownerAndAdminCannotWithdraw() {
    Member owner = newMember("wd6", UserRole.OWNER, FUNDED);
    Member admin = newMember("wd7", UserRole.ADMIN, FUNDED);

    assertFails(
        () -> withdrawalService.settle(owner.userId(), owner.walletId(), FUNDED, key()),
        AuthErrorCode.WITHDRAW_NOT_ALLOWED);
    assertFails(
        () -> withdrawalService.settle(admin.userId(), admin.walletId(), FUNDED, key()),
        AuthErrorCode.WITHDRAW_NOT_ALLOWED);
    assertFails(
        () -> withdrawalService.preview(owner.userId(), owner.walletId()),
        AuthErrorCode.WITHDRAW_NOT_ALLOWED);

    assertThat(userStatus(owner)).isEqualTo("ACTIVE");
    assertThat(walletBalance(owner)).isEqualTo(FUNDED);
    assertThat(count("refund", "wallet_id", owner.walletId())).isZero();
  }

  // 목적: 남의 지갑 번호를 넘겨도 탈퇴·환불이 일어나지 않는지 확인한다(방어선. 컨트롤러가 토큰의 회원으로 지갑을 정한다).
  @Test
  @DisplayName("다른 회원의 지갑으로는 탈퇴할 수 없다")
  void othersWalletIsRejected() {
    Member me = newMember("wd8", UserRole.USER, FUNDED);
    Member other = newMember("wd9", UserRole.USER, FUNDED);

    assertFails(
        () -> withdrawalService.settle(me.userId(), other.walletId(), FUNDED, key()),
        CommonErrorCode.FORBIDDEN);

    assertThat(walletBalance(other)).isEqualTo(FUNDED);
    assertThat(walletStatus(other)).isEqualTo("ACTIVE");
    assertThat(userStatus(me)).isEqualTo("ACTIVE");
  }

  // 목적: 이미 탈퇴한 회원이 같은 요청을 다시 보내면 거절되고 환불이 두 번 일어나지 않는지 확인한다.
  @Test
  @DisplayName("이미 탈퇴한 회원의 두 번째 요청은 거절되고 환불은 한 번뿐이다")
  void secondRequestIsRejected() {
    Member m = newMember("wd10", UserRole.USER, FUNDED);
    String key = key();
    withdrawalService.settle(m.userId(), m.walletId(), FUNDED, key);

    assertFails(
        () -> withdrawalService.settle(m.userId(), m.walletId(), FUNDED, key),
        AuthErrorCode.WITHDRAWN_USER);

    assertThat(accountBalance(m)).isEqualTo(m.accountStart() + FUNDED);
    assertThat(count("refund", "wallet_id", m.walletId())).isEqualTo(1);
  }

  // 목적: 같은 회원이 동시에 두 번 탈퇴를 요청해도 환불이 한 번만 일어나는지 확인한다(지갑 행 잠금).
  @Test
  @DisplayName("동시에 두 번 요청해도 한 번만 탈퇴하고 환불도 한 번뿐이다")
  void concurrentSettle() throws Exception {
    Member m = newMember("wd11", UserRole.USER, FUNDED);
    CyclicBarrier startTogether = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    int success = 0;
    try {
      List<Future<ErrorCode>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        String key = key();
        futures.add(
            pool.submit(
                () -> {
                  startTogether.await(10, TimeUnit.SECONDS);
                  try {
                    withdrawalService.settle(m.userId(), m.walletId(), FUNDED, key);
                    return null;
                  } catch (BusinessException e) {
                    return e.getErrorCode();
                  }
                }));
      }
      for (Future<ErrorCode> future : futures) {
        ErrorCode code = future.get(60, TimeUnit.SECONDS);
        if (code == null) {
          success++;
        } else {
          // 두 번째는 지갑 락을 기다렸다가 탈퇴한 지갑이라(WAL-002), 또는 이미 탈퇴한 회원이라(AUTH-004) 거절된다
          assertThat(code).isIn(WalletErrorCode.WALLET_WITHDRAWN, AuthErrorCode.WITHDRAWN_USER);
        }
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(success).isEqualTo(1);
    assertThat(accountBalance(m)).isEqualTo(m.accountStart() + FUNDED);
    assertThat(count("refund", "wallet_id", m.walletId())).isEqualTo(1);
    assertThat(walletBalance(m)).isZero();
  }

  // 목적: 다른 거래(충전·결제)가 지갑을 잠그고 있으면 탈퇴가 그것이 끝나기를 기다렸다가 "바뀐 최신 잔액"을 보고 거절하는지 확인한다.
  // 잠그지 않고 읽으면 옛 잔액으로 환불해서, 그 사이 들어온 충전금이 탈퇴한 지갑에 남는다.
  @Test
  @DisplayName("지갑이 잠겨 있으면 기다렸다가 바뀐 잔액을 보고 AUTH-008 로 거절한다")
  void waitsForWalletLock() throws Exception {
    Member m = newMember("wd13", UserRole.USER, FUNDED);
    TransactionTemplate tx = new TransactionTemplate(txManager);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // 다른 거래가 지갑을 잠그고 1000원을 더하는 중
      Future<?> other =
          pool.submit(
              () ->
                  tx.executeWithoutResult(
                      status -> {
                        walletRepository
                            .findByIdForUpdate(m.walletId())
                            .orElseThrow()
                            .credit(1_000L, false, NOW);
                        locked.countDown();
                        try {
                          release.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                      }));
      locked.await(10, TimeUnit.SECONDS);

      Future<ErrorCode> settle =
          pool.submit(
              () -> {
                try {
                  withdrawalService.settle(m.userId(), m.walletId(), FUNDED, key());
                  return null;
                } catch (BusinessException e) {
                  return e.getErrorCode();
                }
              });
      // 잠금이 풀리기 전에는 탈퇴가 끝나지 않고 기다려야 한다
      assertThatThrownBy(() -> settle.get(700, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);

      release.countDown();
      other.get(10, TimeUnit.SECONDS);

      assertThat(settle.get(30, TimeUnit.SECONDS)).isEqualTo(AuthErrorCode.WITHDRAW_AMOUNT_CHANGED);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }

    assertThat(walletBalance(m)).isEqualTo(FUNDED + 1_000L); // 충전금은 그대로, 환불은 일어나지 않음
    assertThat(walletStatus(m)).isEqualTo("ACTIVE");
    assertThat(userStatus(m)).isEqualTo("ACTIVE");
    assertThat(count("refund", "wallet_id", m.walletId())).isZero();
  }

  // 목적: 미리보기가 환불 예정액과 소멸될 적립 예정 건을 보여 주고, 아무것도 바꾸지 않는지 확인한다.
  @Test
  @DisplayName("미리보기는 환불 예정액과 소멸될 적립 예정을 보여 주고 아무것도 바꾸지 않는다")
  void preview() {
    Member m = newMember("wd12", UserRole.USER, FUNDED);
    pending(m, 9_831L, 700L);
    pending(m, 9_832L, 800L);
    jdbcTemplate.update("update wallet set cashback_balance = -200 where id = ?", m.walletId());

    WithdrawalPreviewResponse preview = withdrawalService.preview(m.userId(), m.walletId());

    assertThat(preview.refundAmount()).isEqualTo(FUNDED);
    assertThat(preview.pendingCashbackCount()).isEqualTo(2);
    assertThat(preview.pendingCashbackAmount()).isEqualTo(1_500L);
    assertThat(preview.cashbackBalance()).isEqualTo(-200L);
    assertThat(walletBalance(m)).isEqualTo(FUNDED);
    assertThat(walletStatus(m)).isEqualTo("ACTIVE");
    assertThat(userStatus(m)).isEqualTo("ACTIVE");
    verify(cashbackService, never()).cancel(anyLong());
  }
}
