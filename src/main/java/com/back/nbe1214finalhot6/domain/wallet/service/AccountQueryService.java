package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.wallet.dto.AccountResponse;
import com.back.nbe1214finalhot6.domain.wallet.dto.AccountTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import com.back.nbe1214finalhot6.domain.wallet.exception.WalletErrorCode;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountRepository;
import com.back.nbe1214finalhot6.domain.wallet.repository.AccountTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 모의계좌 잔액·거래 내역 조회. 읽기만 한다. 어느 계좌를 볼지는 요청이 아니라 호출한 쪽이 넘겨 준 회원 번호(토큰의 회원)로 정하므로 남의 계좌를 볼 방법이
 * 없다.
 */
@Service
@RequiredArgsConstructor
public class AccountQueryService {

  static final int MAX_PAGE_SIZE = 100;

  private final AccountRepository accountRepository;
  private final AccountTransactionRepository accountTransactionRepository;

  /** 내 모의계좌 잔액. 계좌가 없으면 WAL-008(404). */
  @Transactional(readOnly = true)
  public AccountResponse getAccount(Long userId) {
    return new AccountResponse(accountOf(userId).getBalance());
  }

  /**
   * 내 모의계좌 거래 내역을 최신순으로 돌려준다. 같은 시각이면 나중에 남은 행이 먼저 온다.
   *
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   */
  @Transactional(readOnly = true)
  public PageResponse<AccountTransactionResponse> getTransactions(Long userId, int page, int size) {
    Long accountId = accountOf(userId).getId();
    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    return PageResponse.from(
        accountTransactionRepository
            .findByAccountId(accountId, pageable)
            .map(AccountTransactionResponse::from));
  }

  private Account accountOf(Long userId) {
    return accountRepository
        .findByUserId(userId)
        .orElseThrow(() -> new BusinessException(WalletErrorCode.ACCOUNT_NOT_FOUND));
  }
}
