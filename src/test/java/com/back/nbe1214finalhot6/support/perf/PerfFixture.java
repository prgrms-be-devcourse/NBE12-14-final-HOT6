package com.back.nbe1214finalhot6.support.perf;

import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.service.CashbackBudgetService;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentRequest;
import com.back.nbe1214finalhot6.domain.payment.dto.PaymentResponse;
import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.payment.service.PaymentFacade;
import com.back.nbe1214finalhot6.domain.qr.dto.QrCreateRequest;
import com.back.nbe1214finalhot6.domain.qr.service.QrService;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import com.back.nbe1214finalhot6.domain.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 성능 측정용 준비·확인 도구. 사용하는 측정 클래스에 {@code @Import(PerfFixture.class)} 를 붙인다.
 *
 * <p>기존 {@code PaymentTestFixture} 와 다른 점: QR 을 Mock 이 아니라 <b>실제 QR 서비스로 만든다</b>. 같은 QR 동시 결제에서 QR 조건부
 * UPDATE 경합이 실제로 일어나야 하기 때문이다. 캐시백 예산도 실제로 넣어서, 결제가 예산 행 락(락 순서의 마지막)까지 거치게 한다.
 * 예산이 없으면 적립 예약이 예산 락을 건너뛰어 실제보다 경합이 적게 측정된다.
 *
 * <p>측정은 여러 스레드가 각자 커밋해야 해서 테스트 트랜잭션으로 감싸지 않는다. 그래서 라운드마다 새 지갑·QR 을 만들어 서로 섞이지 않게 한다.
 * 확인용 조회는 JPA 가 아니라 JdbcTemplate 으로 DB 의 현재 값을 직접 읽는다(영속성 컨텍스트의 낡은 값을 보지 않기 위해).
 */
@TestComponent
@RequiredArgsConstructor
public class PerfFixture {

  // 예산은 측정 중에 바닥나지 않을 만큼 크게 잡는다(소진되면 적립 제외로 흐름이 바뀌어 비교가 흐려진다)
  static final long BUDGET = 10_000_000_000L;
  static final BigDecimal RATE = new BigDecimal("0.03");

  // 지갑의 user_id 는 유니크라 겹치지 않는 값을 쓴다. 기존 픽스처와 겹치지 않게 큰 수에서 시작한다
  private static final AtomicLong SEQ = new AtomicLong(9_000_000_000_000L + System.nanoTime() % 1_000_000_000L);

  private final MerchantRepository merchantRepository;
  private final WalletRepository walletRepository;
  private final WalletService walletService;
  private final QrService qrService;
  private final CashbackBudgetService budgetService;
  private final PaymentFacade paymentFacade;
  private final JdbcTemplate jdbc;
  private final PlatformTransactionManager transactionManager;
  private final Clock clock;

  // ---------- 준비 ----------

  /**
   * 지금(테스트 시계) 달의 연·월 캐시백 예산을 넉넉하게 넣는다. 여러 번 불러도 된다.
   *
   * <p>이미 있으면 건드리지 않는다. 한 번이라도 확보(결제)가 일어난 월 예산은 수정할 수 없게 막혀 있기 때문이다(캐시백 규칙).
   */
  public void ensureBudget() {
    YearMonth month = YearMonth.now(clock);
    budgetService.createAnnualIfAbsent(month.getYear(), BUDGET);
    Long monthly =
        jdbc.queryForObject(
            "select count(*) from cashback_monthly_budget where budget_year = ? and budget_month = ?",
            Long.class,
            month.getYear(),
            month.getMonthValue());
    if (monthly == 0) {
      budgetService.putMonthly(
          month.getYear(), month.getMonthValue(), new MonthlyBudgetRequest(BUDGET, RATE));
    }
  }

  /** 적립 대상 가맹점을 만든다. */
  public Long merchant() {
    return merchantRepository.save(Merchant.create(null, "측정가맹점", "서울", true)).getId();
  }

  /** 데브머니 잔액을 가진 지갑을 만든다(캐시백 잔액 0). */
  public Long wallet(long devMoney) {
    Long walletId =
        walletRepository.save(Wallet.create(SEQ.incrementAndGet(), LocalDateTime.now(clock))).getId();
    if (devMoney > 0) {
      walletService.credit(walletId, devMoney, WalletTransactionType.CHARGE, null);
    }
    return walletId;
  }

  /** 실제 QR 서비스로 결제 대기(WAITING) QR 을 만들고 토큰을 돌려준다. */
  public String qr(Long merchantId, long amount) {
    return qrService.createDynamic(merchantId, new QrCreateRequest(amount)).token();
  }

  /** 결제 요청 본문. 캐시백은 쓰지 않는다(데브머니 전액). */
  public PaymentRequest payRequest(Long walletId, String qrToken) {
    return new PaymentRequest(walletId, qrToken, 0L);
  }

  public static String newKey() {
    return UUID.randomUUID().toString();
  }

  /** 취소 측정용: 실제로 완료된 결제 1건을 만든다. */
  public CompletedPayment completedPayment(long amount) {
    Long merchantId = merchant();
    Long walletId = wallet(amount);
    String token = qr(merchantId, amount);
    PaymentResponse response = paymentFacade.pay(newKey(), payRequest(walletId, token));
    if (response.status() != PaymentStatus.COMPLETED) {
      throw new IllegalStateException("준비용 결제가 완료되지 않았습니다: " + response.status());
    }
    return new CompletedPayment(merchantId, walletId, response.paymentId(), amount);
  }

  public record CompletedPayment(Long merchantId, Long walletId, Long paymentId, long amount) {}

  /**
   * 장애 재현용: 지갑 행을 잠근 채 holdMillis 동안 놓지 않는다(느린 트랜잭션 흉내). 커넥션 1개를 그동안 붙잡는다.
   *
   * <p>결제가 같은 지갑을 잠그려 하면 이 트랜잭션이 끝날 때까지 기다린다. 지금 코드에는 락 대기 상한이 없어서 기다리는 시간이 그대로 응답 시간이 된다.
   */
  public String holdWalletLock(Long walletId, long holdMillis) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              jdbc.queryForObject("select id from wallet where id = ? for update", Long.class, walletId);
              try {
                Thread.sleep(holdMillis);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return "HELD";
            });
  }

  // ---------- 확인(불변 조건) ----------

  /** 지갑의 데브머니 잔액(DB 현재 값). */
  public long balance(Long walletId) {
    return jdbc.queryForObject("select balance from wallet where id = ?", Long.class, walletId);
  }

  /** (지갑, 멱등키)로 만들어진 결제 행 수. 같은 키 동시 요청이면 1 이어야 한다. */
  public long paymentRows(Long walletId, String key) {
    return jdbc.queryForObject(
        "select count(*) from payment where wallet_id = ? and idempotency_key = ?",
        Long.class,
        walletId,
        key);
  }

  /** 지갑의 상태별 결제 수. */
  public long payments(Long walletId, PaymentStatus status) {
    return jdbc.queryForObject(
        "select count(*) from payment where wallet_id = ? and status = ?",
        Long.class,
        walletId,
        status.name());
  }

  /** QR 로 완료된 결제 수(지갑 무관). 같은 QR 동시 결제면 1 이어야 한다. */
  public long completedPaymentsByQr(String qrToken) {
    return jdbc.queryForObject(
        "select count(*) from payment where qr_token = ? and status = 'COMPLETED'",
        Long.class,
        qrToken);
  }

  /** QR 의 DB 상태(WAITING / USED). */
  public String qrStatus(String qrToken) {
    return jdbc.queryForObject("select status from qr_code where token = ?", String.class, qrToken);
  }

  /** 지갑 원장에서 종류별 거래 줄 수. 결제 차감·취소 환급이 몇 번 반영됐는지 본다. */
  public long ledgerLines(Long walletId, WalletTransactionType type) {
    return jdbc.queryForObject(
        "select count(*) from wallet_transaction where wallet_id = ? and type = ?",
        Long.class,
        walletId,
        type.name());
  }

  /** 결제의 적립 건 상태(없으면 null). 취소되면 CANCELED 여야 한다. */
  public String earningStatus(Long paymentId) {
    return jdbc.query(
            "select status from cashback_earning where payment_id = ?",
            (rs, i) -> rs.getString(1),
            paymentId)
        .stream()
        .findFirst()
        .orElse(null);
  }
}
