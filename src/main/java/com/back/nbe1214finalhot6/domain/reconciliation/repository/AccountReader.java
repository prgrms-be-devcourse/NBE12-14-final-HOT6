package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.Account;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 대사가 모의 계좌를 읽기만 하는 저장소. */
public interface AccountReader extends Repository<Account, Long> {

  long count();

  // 잔액과 원장 합을 한 쿼리에서 비교해야 그 사이에 커밋된 거래 때문에 어긋나 보이지 않는다
  @Query(
      """
      SELECT a.id FROM Account a
      WHERE a.balance <> (
        SELECT COALESCE(SUM(t.amount), 0L) FROM AccountTransaction t WHERE t.accountId = a.id)
      ORDER BY a.id
      """)
  List<Long> findBalanceMismatchIds();
}
