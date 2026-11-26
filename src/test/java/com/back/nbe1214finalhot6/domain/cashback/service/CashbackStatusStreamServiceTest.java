package com.back.nbe1214finalhot6.domain.cashback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.domain.cashback.dto.AnnualBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackStatusResponse;
import com.back.nbe1214finalhot6.domain.cashback.dto.MonthlyBudgetRequest;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackAnnualBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackBudgetStateLogRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackMonthlyBudgetRepository;
import com.back.nbe1214finalhot6.domain.cashback.repository.CashbackNoticeRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 적립 상태 스트림이 중단 전이가 커밋된 뒤에만 중단을 보내는지 확인한다.
 *
 * <p>커밋 뒤에 보내는 동작을 봐야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class CashbackStatusStreamServiceTest {

  private static final int YEAR = 2026;
  private static final int MONTH = 11;

  @Autowired private CashbackStatusStreamService streamService;
  @Autowired private CashbackBudgetService budgetService;
  @Autowired private CashbackBudgetStateService stateService;
  @Autowired private CashbackAnnualBudgetRepository annualBudgetRepository;
  @Autowired private CashbackMonthlyBudgetRepository monthlyBudgetRepository;
  @Autowired private CashbackBudgetStateLogRepository stateLogRepository;
  @Autowired private CashbackNoticeRepository noticeRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private MutableClock clock;

  /** 보낸 상태를 모아 두는 연결. */
  private static class RecordingEmitter extends SseEmitter {
    final BlockingQueue<Boolean> received = new LinkedBlockingQueue<>();

    @Override
    public void send(SseEventBuilder builder) throws IOException {
      builder.build().stream()
          .map(DataWithMediaType::getData)
          .filter(CashbackStatusResponse.class::isInstance)
          .forEach(data -> received.add(((CashbackStatusResponse) data).paused()));
    }

    Boolean next() throws InterruptedException {
      return received.poll(5, TimeUnit.SECONDS);
    }
  }

  /** 화면이 닫혀 보내기에 실패하는 연결. */
  private static class BrokenEmitter extends SseEmitter {
    @Override
    public void send(SseEventBuilder builder) throws IOException {
      throw new IOException("closed");
    }
  }

  @BeforeEach
  void setUpBudget() {
    budgetService.putAnnual(YEAR, new AnnualBudgetRequest(12_000_000L));
    budgetService.putMonthly(
        YEAR, MONTH, new MonthlyBudgetRequest(1_000_000L, new BigDecimal("0.03")));
  }

  @AfterEach
  void cleanUp() {
    stateLogRepository.deleteAll();
    noticeRepository.deleteAll();
    monthlyBudgetRepository.deleteAll();
    annualBudgetRepository.deleteAll();
  }

  private RecordingEmitter connect() throws InterruptedException {
    RecordingEmitter emitter = new RecordingEmitter();
    streamService.register(emitter);
    assertThat(emitter.next()).as("연결 직후 상태").isFalse();
    return emitter;
  }

  // 목적: 연결 직후 현재 상태를 보내고, 운영자 긴급 중단이 커밋되면 바로 중단을 보내는지 확인한다.
  @Test
  @DisplayName("긴급 중단이 커밋되면 중단 상태를 보낸다")
  void manualEmergencyStop() throws Exception {
    RecordingEmitter emitter = connect();

    stateService.emergencyStopManually(YEAR, MONTH);

    assertThat(emitter.next()).isTrue();
  }

  // 목적: 결제의 예산 확보로 긴급선에 닿아 자동 중단될 때도 보내는지 확인한다.
  @Test
  @DisplayName("예산 확보로 자동 중단되면 중단 상태를 보낸다")
  void automaticStop() throws Exception {
    RecordingEmitter emitter = connect();

    budgetService.reserveBudget(YEAR, MONTH, 960_000L);

    assertThat(emitter.next()).isTrue();
  }

  // 목적: 공지 후 중단은 공지 단계에서는 보내지 않고, 예약 시각이 되어 중단될 때 보내는지 확인한다.
  @Test
  @DisplayName("예약 시각에 중단되면 중단 상태를 보낸다")
  void scheduledStop() throws Exception {
    RecordingEmitter emitter = connect();
    stateService.noticeStopManually(YEAR, MONTH);
    assertThat(emitter.received.poll(300, TimeUnit.MILLISECONDS)).isNotEqualTo(Boolean.TRUE);

    clock.advance(Duration.ofDays(4));
    assertThat(stateService.stopDueScheduled()).isEqualTo(1);

    assertThat(emitter.next()).isTrue();
  }

  // 목적: 중단한 트랜잭션이 롤백되면 중단을 보내지 않는지 확인한다.
  @Test
  @DisplayName("롤백되면 중단 상태를 보내지 않는다")
  void rollbackSendsNothing() throws Exception {
    RecordingEmitter emitter = connect();

    transactionTemplate.executeWithoutResult(
        status -> {
          stateService.emergencyStopManually(YEAR, MONTH);
          status.setRollbackOnly();
        });

    assertThat(emitter.received.poll(500, TimeUnit.MILLISECONDS)).isNotEqualTo(Boolean.TRUE);
  }

  // 목적: 보내기에 실패한 연결은 목록에서 지우고, 다른 연결에는 계속 보내는지 확인한다.
  @Test
  @DisplayName("끊긴 연결은 지우고 나머지에는 계속 보낸다")
  void removesBrokenConnection() throws Exception {
    int before = streamService.subscriberCount();
    streamService.register(new BrokenEmitter());
    RecordingEmitter alive = connect(); // 전송은 순서대로라 이 연결이 받으면 앞의 실패 처리도 끝났다
    assertThat(streamService.subscriberCount()).isEqualTo(before + 1);

    stateService.emergencyStopManually(YEAR, MONTH);

    assertThat(alive.next()).isTrue();
  }
}
