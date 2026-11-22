package com.back.nbe1214finalhot6.domain.qr.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.qr.entity.QrCode;
import com.back.nbe1214finalhot6.domain.qr.entity.QrStatus;
import com.back.nbe1214finalhot6.domain.qr.exception.QrErrorCode;
import com.back.nbe1214finalhot6.domain.qr.repository.QrCodeRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import com.back.nbe1214finalhot6.support.MutableClock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// 여러 스레드가 각자 트랜잭션으로 커밋해야 하므로 @Transactional 을 붙이지 않고, 끝나면 직접 지운다.
@IntegrationTest
class QrServiceConcurrencyTest {

  private static final int THREADS = 8;

  @Autowired private QrService qrService;
  @Autowired private QrCodeRepository qrCodeRepository;
  @Autowired private MutableClock clock;

  @Test
  @DisplayName("같은 QR 을 동시에 사용 처리해도 한 번만 성공한다")
  void 동시에사용처리해도한번만성공() throws Exception {
    LocalDateTime now = LocalDateTime.now(clock);
    QrCode qr = qrCodeRepository.save(QrCode.createDynamic(1L, 1000L, now));
    Long qrId = qr.getId();
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);

    try {
      List<Future<QrErrorCode>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        Callable<QrErrorCode> task =
            () -> {
              ready.countDown();
              start.await();
              try {
                qrService.markUsed(qr.getToken(), now);
                return null;
              } catch (BusinessException e) {
                return (QrErrorCode) e.getErrorCode();
              }
            };
        futures.add(executor.submit(task));
      }
      ready.await();
      start.countDown();

      int success = 0;
      int alreadyUsed = 0;
      for (Future<QrErrorCode> future : futures) {
        QrErrorCode error = future.get(10, TimeUnit.SECONDS);
        if (error == null) {
          success++;
        } else if (error == QrErrorCode.QR_ALREADY_USED) {
          alreadyUsed++;
        }
      }

      assertThat(success).isEqualTo(1);
      assertThat(alreadyUsed).isEqualTo(THREADS - 1);
      assertThat(qrCodeRepository.findById(qrId).orElseThrow().getStatus())
          .isEqualTo(QrStatus.USED);
    } finally {
      executor.shutdownNow();
      qrCodeRepository.deleteById(qrId);
    }
  }
}
