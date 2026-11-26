package com.back.nbe1214finalhot6.support.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.payment.entity.PaymentStatus;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.perf.PerfFixture.CompletedPayment;
import com.back.nbe1214finalhot6.support.perf.PerfReport.Environment;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 측정 픽스처가 시나리오에 필요한 상태를 실제로 만드는지 확인한다. 측정 전에 준비가 틀리면 수치도 틀리기 때문이다.
 *
 * <p>캐시백 예산을 DB 에 남기므로(예산이 없다고 가정하는 일반 테스트와 섞이면 안 됨) perf 태그를 붙여 perfTest 에서만 돈다.
 */
@Tag("perf")
@IntegrationTest
@Import(PerfFixture.class)
class PerfFixtureTest {

  @Autowired PerfFixture fixture;
  @Autowired DataSource dataSource;

  @BeforeEach
  void setUp() {
    fixture.ensureBudget();
  }

  @Test
  @DisplayName("예산은 여러 번 넣어도 된다")
  void 예산_반복() {
    fixture.ensureBudget();
    fixture.ensureBudget();
  }

  @Test
  @DisplayName("실제 QR 서비스로 만든 QR 은 결제 대기(WAITING) 상태다")
  void 실제_QR() {
    String token = fixture.qr(fixture.merchant(), 5_000L);

    assertThat(fixture.qrStatus(token)).isEqualTo("WAITING");
  }

  @Test
  @DisplayName("완료된 결제 준비: 결제 완료, QR 사용됨, 지갑 차감 1줄, 예산을 확보한 적립 건(PENDING)까지 생긴다")
  void 완료된_결제() {
    CompletedPayment p = fixture.completedPayment(10_000L);

    assertThat(fixture.payments(p.walletId(), PaymentStatus.COMPLETED)).isEqualTo(1);
    assertThat(fixture.balance(p.walletId())).isZero();
    assertThat(fixture.ledgerLines(p.walletId(), WalletTransactionType.PAYMENT_DEVMONEY))
        .isEqualTo(1);
    // 적립 건이 PENDING 이면 예산 행 락까지 거친 것이다(예산이 없으면 제외 사유만 남는다)
    assertThat(fixture.earningStatus(p.paymentId())).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("측정 환경(PostgreSQL 버전, 커넥션 풀 크기)을 DB 에서 읽는다")
  void 측정_환경() {
    Environment env = PerfReport.environment(dataSource);

    assertThat(env.postgresVersion()).isNotBlank();
    assertThat(env.poolSize()).isEqualTo(2); // 테스트 프로필 기본값. 시나리오는 클래스별로 바꿔서 쓴다
  }
}
