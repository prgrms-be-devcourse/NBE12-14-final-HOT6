package com.back.nbe1214finalhot6.domain.merchant.service;

import com.back.nbe1214finalhot6.domain.merchant.dto.MerchantRegisterRequest;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantAccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.MerchantWalletRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @Transactional을 붙이지 않는다. 테스트가 직접 바깥 트랜잭션을 열어야 롤백 여부를 확인할 수 있다
@IntegrationTest
class MerchantRegisterRollbackTest {

    @Autowired MerchantService merchantService;
    @Autowired MerchantRepository merchantRepository;
    @Autowired MerchantWalletRepository merchantWalletRepository;
    @Autowired MerchantAccountRepository merchantAccountRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        merchantWalletRepository.deleteAll();
        merchantAccountRepository.deleteAll();
        merchantRepository.deleteAll();
    }

    @Test
    @DisplayName("가맹점 등록 트랜잭션이 롤백되면 가맹점, 지갑, 계좌가 모두 함께 사라진다")
    void 등록트랜잭션_롤백시_가맹점_지갑_계좌_모두_롤백() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(
                () ->
                        transaction.executeWithoutResult(
                                status -> {
                                    merchantService.register(
                                            new MerchantRegisterRequest(1L, "롤백 가맹점", "인천광역시 남동구 어딘가 9", null));
                                    throw new IllegalStateException("등록 이후 실패");
                                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(merchantRepository.count()).isZero();
        assertThat(merchantWalletRepository.count()).isZero();
        assertThat(merchantAccountRepository.count()).isZero();
    }
}