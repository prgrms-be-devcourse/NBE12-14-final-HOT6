package com.back.nbe1214finalhot6.domain.reconciliation.repository;

import com.back.nbe1214finalhot6.domain.wallet.entity.Wallet;
import com.back.nbe1214finalhot6.domain.wallet.entity.WalletTransactionType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 대사가 회원 지갑을 읽기만 하는 저장소. 잔액과 원장 합은 한 쿼리에서 비교한다. */
public interface UserWalletReader extends Repository<Wallet, Long> {

  long count();

  @Query(
      """
      SELECT w.id FROM Wallet w
      WHERE w.balance <> (
        SELECT COALESCE(SUM(t.amount), 0L) FROM WalletTransaction t
        WHERE t.walletId = w.id AND t.type IN :types)
      ORDER BY w.id
      """)
  List<Long> findDevMoneyMismatchIds(@Param("types") Collection<WalletTransactionType> types);

  @Query(
      """
      SELECT w.id FROM Wallet w
      WHERE w.cashbackBalance <> (
        SELECT COALESCE(SUM(t.amount), 0L) FROM WalletTransaction t
        WHERE t.walletId = w.id AND t.type IN :types)
      ORDER BY w.id
      """)
  List<Long> findCashbackMismatchIds(@Param("types") Collection<WalletTransactionType> types);
}
