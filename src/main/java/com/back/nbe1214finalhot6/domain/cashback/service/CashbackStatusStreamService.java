package com.back.nbe1214finalhot6.domain.cashback.service;

import com.back.nbe1214finalhot6.domain.cashback.dto.CashbackStatusResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 적립 상태를 열려 있는 화면에 실시간으로 보낸다(SSE). 연결 직후, 중단 전이가 커밋된 직후, 그리고 20초마다 현재 상태를 보낸다. 주기 전송은 중간 서버가 조용한
 * 연결을 끊지 않게 하고, 놓친 변화와 월이 바뀐 것을 반영한다.
 *
 * <p>상태 조회와 전송은 전용 스레드 하나에서 순서대로 한다. 느린 연결이 결제 트랜잭션의 응답을 늦추지 않고, 요청 스레드가 DB 를 읽지 않아 연결이 열린 동안
 * DB 연결을 붙잡지 않는다. 연결 목록은 서버 메모리에 두므로 서버 한 대 구성을 전제로 한다.
 */
@Slf4j
@Service
public class CashbackStatusStreamService {

  // 이 시간이 지나면 서버가 연결을 끝내고, 화면은 토큰을 다시 실어 연결한다
  private static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(5);
  private static final long HEARTBEAT_MILLIS = 20_000;

  private final CashbackBudgetService budgetService;
  private final Clock clock;
  private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
  private final ExecutorService sender =
      Executors.newSingleThreadExecutor(
          task -> {
            Thread thread = new Thread(task, "cashback-status-sender");
            thread.setDaemon(true);
            return thread;
          });

  public CashbackStatusStreamService(CashbackBudgetService budgetService, Clock clock) {
    this.budgetService = budgetService;
    this.clock = clock;
  }

  /** 새 연결을 만들고 현재 상태를 보낸다. */
  public SseEmitter subscribe() {
    SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT.toMillis());
    register(emitter);
    return emitter;
  }

  void register(SseEmitter emitter) {
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(emitter::complete);
    emitter.onError(error -> emitters.remove(emitter));
    emitters.add(emitter);
    submit(() -> send(List.of(emitter)));
  }

  /** 중단 전이가 커밋된 뒤에만 불린다. 롤백되면 보내지 않는다. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onBudgetStopped(BudgetStoppedEvent event) {
    submit(() -> send(emitters));
  }

  @Scheduled(fixedRate = HEARTBEAT_MILLIS)
  public void heartbeat() {
    submit(() -> send(emitters));
  }

  int subscriberCount() {
    return emitters.size();
  }

  // 서버를 끌 때 열린 연결이 종료를 붙잡지 않게 먼저 끝낸다
  @EventListener(ContextClosedEvent.class)
  public void close() {
    sender.shutdownNow();
    emitters.forEach(SseEmitter::complete);
    emitters.clear();
  }

  private void submit(Runnable task) {
    try {
      sender.execute(task);
    } catch (RejectedExecutionException e) {
      log.debug("서버를 끄는 중이라 적립 상태를 보내지 않습니다");
    }
  }

  private void send(Collection<SseEmitter> targets) {
    if (targets.isEmpty()) {
      return;
    }
    CashbackStatusResponse status;
    try {
      YearMonth month = YearMonth.now(clock);
      status =
          new CashbackStatusResponse(budgetService.isPaused(month.getYear(), month.getMonthValue()));
    } catch (RuntimeException e) {
      log.warn("적립 상태를 읽지 못했습니다. 다음 전송 때 다시 보냅니다", e);
      return;
    }
    for (SseEmitter emitter : targets) {
      try {
        emitter.send(SseEmitter.event().name("status").data(status, MediaType.APPLICATION_JSON));
      } catch (IOException | RuntimeException e) {
        // 화면을 닫았거나 이미 끝난 연결
        emitters.remove(emitter);
      }
    }
  }
}
