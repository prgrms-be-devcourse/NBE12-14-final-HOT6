package com.back.nbe1214finalhot6.domain.wallet.service;

import com.back.nbe1214finalhot6.common.response.PageResponse;
import com.back.nbe1214finalhot6.domain.wallet.dto.WalletTransactionResponse;
import com.back.nbe1214finalhot6.domain.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지갑 거래 내역 조회. 원장을 읽기만 한다. 어느 지갑을 볼지는 호출한 쪽(컨트롤러)이 토큰의 회원으로 정한다. */
@Service
@RequiredArgsConstructor
public class WalletTransactionService {

  static final int MAX_PAGE_SIZE = 100;

  private final WalletTransactionRepository walletTransactionRepository;

  /**
   * 지갑의 거래 내역을 최신순으로 돌려준다. 같은 시각이면 나중에 남은 행이 먼저 온다.
   *
   * @param size 한 페이지 건수. {@value #MAX_PAGE_SIZE} 을 넘으면 그 값으로 줄인다
   */
  @Transactional(readOnly = true)
  public PageResponse<WalletTransactionResponse> getTransactions(Long walletId, int page, int size) {
    Pageable pageable =
        PageRequest.of(
            page,
            Math.min(size, MAX_PAGE_SIZE),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    return PageResponse.from(
        walletTransactionRepository
            .findByWalletId(walletId, pageable)
            .map(WalletTransactionResponse::from));
  }
}
