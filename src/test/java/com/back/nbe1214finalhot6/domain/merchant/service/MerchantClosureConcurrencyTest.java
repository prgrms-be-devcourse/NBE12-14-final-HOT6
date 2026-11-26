package com.back.nbe1214finalhot6.domain.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.exception.MerchantErrorCode;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantClosureRepository;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.wallet.entity.MerchantAccount;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
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
class MerchantClosureConcurrencyTest {

  private static final int THREADS = 8;

  @Autowired private MerchantClosureService closureService;
  @Autowired private MerchantRepository merchantRepository;
  @Autowired private MerchantClosureRepository closureRepository;
  @Autowired private MerchantAccountRepository accountRepository;

  @Test
  @DisplayName("같은 가맹점이 동시에 해지를 신청해도 한 번만 접수되고 나머지는 MER-003 이다")
  void 동시에_신청해도_한번만_접수() throws Exception {
    Merchant merchant =
        merchantRepository.save(Merchant.create(9_101L, "동시 해지 가맹점", "인천광역시 남동구 1", true));
    Long merchantId = merchant.getId();
    accountRepository.save(MerchantAccount.create(merchantId, LocalDateTime.now()));
    ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    CountDownLatch ready = new CountDownLatch(THREADS);
    CountDownLatch start = new CountDownLatch(1);

    try {
      List<Future<MerchantErrorCode>> futures = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        Callable<MerchantErrorCode> task =
            () -> {
              ready.countDown();
              start.await();
              try {
                closureService.request(merchantId);
                return null;
              } catch (BusinessException e) {
                return (MerchantErrorCode) e.getErrorCode();
              }
            };
        futures.add(executor.submit(task));
      }
      ready.await();
      start.countDown();

      int success = 0;
      int closing = 0;
      for (Future<MerchantErrorCode> future : futures) {
        MerchantErrorCode error = future.get(10, TimeUnit.SECONDS);
        if (error == null) {
          success++;
        } else if (error == MerchantErrorCode.MERCHANT_CLOSING) {
          closing++;
        }
      }

      assertThat(success).isEqualTo(1);
      assertThat(closing).isEqualTo(THREADS - 1);
      assertThat(merchantRepository.findStatusById(merchantId)).contains(MerchantStatus.CLOSING);
      assertThat(closureRepository.findByMerchantId(merchantId)).isPresent();
    } finally {
      executor.shutdownNow();
      closureRepository.findByMerchantId(merchantId).ifPresent(closureRepository::delete);
      accountRepository.findByMerchantId(merchantId).ifPresent(accountRepository::delete);
      merchantRepository.deleteById(merchantId);
    }
  }
}
