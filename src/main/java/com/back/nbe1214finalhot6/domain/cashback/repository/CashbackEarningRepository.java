package com.back.nbe1214finalhot6.domain.cashback.repository;

import com.back.nbe1214finalhot6.domain.cashback.entity.CashbackEarning;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashbackEarningRepository extends JpaRepository<CashbackEarning, Long> {

  /** 결제로 적립 건을 찾는다. {@code reserve} 가 같은 결제를 다시 받았는지 판단하는 멱등 키다. */
  Optional<CashbackEarning> findByPaymentId(Long paymentId);
}
