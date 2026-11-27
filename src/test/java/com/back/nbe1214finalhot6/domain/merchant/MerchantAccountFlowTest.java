package com.back.nbe1214finalhot6.domain.merchant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.jwt.JwtProvider;
import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import com.back.nbe1214finalhot6.domain.merchant.entity.MerchantStatus;
import com.back.nbe1214finalhot6.domain.merchant.repository.MerchantRepository;
import com.back.nbe1214finalhot6.domain.settlement.entity.DailySettlement;
import com.back.nbe1214finalhot6.domain.settlement.repository.DailySettlementRepository;
import com.back.nbe1214finalhot6.domain.settlement.service.MonthlySettlementService;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.support.IntegrationTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월 정산금이 들어가는 계좌와 점주 화면이 잔액을 읽는 계좌가 같은지, 실제 보안 설정과 DB 로 끝까지 확인한다.
 *
 * <p>지급은 가맹점의 owner_id 로, 조회는 토큰의 회원 번호로 계좌를 찾는다. 두 경로가 같은 계좌를 가리켜야 지급액이 화면에 그대로 보인다. 테스트 시계는
 * 2026-11-18 이라 10월을 정산할 수 있다. 테스트 전체를 한 트랜잭션으로 감싸서(끝나면 롤백) 따로 정리하지 않는다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MerchantAccountFlowTest {

  private static final long A_OWNER = 9_811L;
  private static final long B_OWNER = 9_812L;
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

  @Autowired MockMvc mockMvc;
  @Autowired JwtProvider jwtProvider;
  @Autowired MerchantRepository merchantRepository;
  @Autowired AccountRepository accountRepository;
  @Autowired DailySettlementRepository dailyRepository;
  @Autowired MonthlySettlementService monthlySettlementService;

  @Test
  @DisplayName("월 정산이 지급되면 점주가 조회하는 계좌 잔액에 지급액이 그대로 보인다")
  void 지급액이_조회_잔액에_반영() throws Exception {
    long merchantId = saveMerchantWithAccount(A_OWNER, "A 가게");
    saveDaily(merchantId, LocalDate.of(2026, 10, 5), 10_000L);
    saveDaily(merchantId, LocalDate.of(2026, 10, 20), 5_000L);

    getAccount(A_OWNER).andExpect(jsonPath("$.data.balance").value(0));

    monthlySettlementService.settle(OCTOBER);

    getAccount(A_OWNER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.balance").value(15_000));
  }

  @Test
  @DisplayName("점주마다 자기 가맹점의 정산금만 자기 계좌에서 보인다")
  void 점주별_계좌_분리() throws Exception {
    long aMerchant = saveMerchantWithAccount(A_OWNER, "A 가게");
    long bMerchant = saveMerchantWithAccount(B_OWNER, "B 가게");
    saveDaily(aMerchant, LocalDate.of(2026, 10, 5), 15_000L);
    saveDaily(bMerchant, LocalDate.of(2026, 10, 5), 7_000L);

    monthlySettlementService.settle(OCTOBER);

    getAccount(A_OWNER).andExpect(jsonPath("$.data.balance").value(15_000));
    getAccount(B_OWNER).andExpect(jsonPath("$.data.balance").value(7_000));
  }

  @Test
  @DisplayName("해지가 끝난 가맹점의 점주도 계좌 잔액은 조회할 수 있다")
  void 해지후에도_잔액_조회() throws Exception {
    long merchantId = saveMerchantWithAccount(A_OWNER, "A 가게");
    saveDaily(merchantId, LocalDate.of(2026, 10, 5), 15_000L);
    monthlySettlementService.settle(OCTOBER);
    merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.ACTIVE, MerchantStatus.CLOSING);
    merchantRepository.updateStatusIfCurrent(merchantId, MerchantStatus.CLOSING, MerchantStatus.CLOSED);

    getAccount(A_OWNER)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.balance").value(15_000));
  }

  @Test
  @DisplayName("가맹점이 없는 점주는 계좌가 있어도 404 MER-001 이다")
  void 가맹점없으면_404() throws Exception {
    accountRepository.save(Account.createEmpty(B_OWNER, LocalDateTime.now()));

    getAccount(B_OWNER)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MER-001"));
  }

  private long saveMerchantWithAccount(long ownerId, String name) {
    accountRepository.save(Account.createEmpty(ownerId, LocalDateTime.now()));
    return merchantRepository.save(Merchant.create(ownerId, name, "인천광역시 남동구 1", true)).getId();
  }

  private void saveDaily(long merchantId, LocalDate date, long paymentTotal) {
    dailyRepository.save(
        DailySettlement.create(merchantId, date, paymentTotal, 0L, 0L, date.atTime(1, 0)));
  }

  private ResultActions getAccount(long ownerId) throws Exception {
    return mockMvc.perform(
        get("/api/merchants/me/account")
            .header("Authorization", "Bearer " + jwtProvider.createAccessToken(ownerId, UserRole.OWNER)));
  }
}
