package com.back.nbe1214finalhot6.setting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.setting.SettingKey;
import com.back.nbe1214finalhot6.setting.entity.OperatorSettingHistory;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingHistoryRepository;
import com.back.nbe1214finalhot6.setting.repository.OperatorSettingRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 키를 동시에 바꿔도 변경 이력이 끊기지 않는지 확인한다.
 *
 * <p>실제 커밋이 오가야 하므로 테스트 트랜잭션으로 감싸지 않고, 끝나면 만든 데이터를 지운다.
 */
@IntegrationTest
class OperatorSettingConcurrencyTest {

  private static final String KEY = SettingKey.WALLET_MONTHLY_CHARGE_LIMIT.name();

  @Autowired private OperatorSettingAdminService adminService;
  @Autowired private OperatorSettingService settingService;
  @Autowired private OperatorSettingRepository settingRepository;
  @Autowired private OperatorSettingHistoryRepository historyRepository;

  @AfterEach
  void cleanUp() {
    historyRepository.deleteAll();
    settingRepository.deleteAll();
  }

  // 목적: 행을 잠그고 읽어서, 같은 키를 8개 스레드가 동시에 서로 다른 값으로 바꿔도 이력이 8건이고 각 변경 전 값이 직전 변경 후 값으로
  // 이어지며 마지막 이력의 변경 후 값이 실제 값과 같은지 확인한다. 잠금이 없으면 두 요청이 같은 변경 전 값을 읽어 이력이 끊긴다.
  @Test
  @DisplayName("같은 키를 동시에 바꿔도 이력이 끊기지 않는다")
  void concurrentPut_keepsHistoryChain() throws Exception {
    adminService.put(KEY, 900_000L); // 행을 먼저 만들어 둔다(처음 설정하는 경쟁은 따로 막지 않는다)
    int threads = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 1; i <= threads; i++) {
        long value = i * 100_000L; // 100,000 ~ 800,000: 모두 서로 다르고 현재 값(900,000)과도 다름
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return adminService.put(KEY, value);
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      for (Future<?> future : futures) {
        future.get(20, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    List<OperatorSettingHistory> history =
        historyRepository.findAllBySettingKeyOrderByIdAsc(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT);
    assertThat(history).hasSize(1 + threads);
    for (int i = 1; i < history.size(); i++) {
      assertThat(history.get(i).getBeforeValue())
          .as("%d 번째 이력의 변경 전 값", i + 1)
          .isEqualTo(history.get(i - 1).getAfterValue());
    }
    assertThat(settingService.getLong(SettingKey.WALLET_MONTHLY_CHARGE_LIMIT))
        .isEqualTo(history.getLast().getAfterValue());
  }
}
