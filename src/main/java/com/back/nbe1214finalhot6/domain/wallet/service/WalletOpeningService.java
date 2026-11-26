package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransaction;
import com.back.nbe1214finalhot6.domain.wallet.entity.AccountTransactionType;
import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원의 모의계좌·가입 보너스 원장·지갑을 만듦. 가입 트랜잭션에 참여함. */
@Service
@RequiredArgsConstructor
public class WalletOpeningService {

  private final AccountRepository accountRepository;
  private final AccountTransactionRepository accountTransactionRepository;
  private final WalletRepository walletRepository;
  private final Clock clock;

  // 트랜잭션을 새로 열지 않고 호출한 쪽(가입)에 참여(REQUIRED). 예외가 나면 회원 저장까지 함께 롤백됨.
  // 같은 회원에게 두 번 만들면 user_id 유니크 제약이 막음
  @Transactional
  public void open(Long userId) {
    LocalDateTime now = LocalDateTime.now(clock);

    // 1. 모의계좌 생성(초기 잔액 500만원)
    Account account = accountRepository.save(Account.create(userId, now));

    // 2. 계좌 원장에 가입 보너스 기록
    accountTransactionRepository.save(
        AccountTransaction.builder()
            .accountId(account.getId())
            .type(AccountTransactionType.SIGNUP_BONUS)
            .amount(Account.INITIAL_BALANCE)
            .balanceAfter(account.getBalance())
            .createdAt(now)
            .build());

    // 3. 지갑 생성(잔액 0, ACTIVE). 지갑에 돈이 들어가는 길은 충전(W3)뿐임
    walletRepository.save(Wallet.create(userId, now));
  }
}
