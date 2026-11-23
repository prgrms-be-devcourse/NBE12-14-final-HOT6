package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import com.back.nbe1214finalhot6.domain.cashback.entity.EarningStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackEarningRepository extends JpaRepository<CashbackEarning, Long> {

  /** 결제로 적립 건을 찾는다. {@code reserve} 가 같은 결제를 다시 받았는지 판단하는 멱등 키다. */
  Optional<CashbackEarning> findByPaymentId(Long paymentId);

  /** 한 이용자(지갑)의 적립 내역. 정렬과 페이지는 호출한 쪽이 정한다. */
  Page<CashbackEarning> findByWalletId(Long walletId, Pageable pageable);

  /** 한 이용자의 적립 내역 중 해당 상태만. */
  Page<CashbackEarning> findByWalletIdAndStatus(
      Long walletId, EarningStatus status, Pageable pageable);
}
