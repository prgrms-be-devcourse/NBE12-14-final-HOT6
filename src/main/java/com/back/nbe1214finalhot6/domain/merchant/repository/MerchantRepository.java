package com.back.nbe1214finalhot6.domain.merchant.repository;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

  Page<Merchant> findByNameContainingIgnoreCase(String name, Pageable pageable);

  // 점주의 가맹점 조회. 점주당 1개가 전제이고, 제약이 생기기 전까지는 가장 먼저 등록한 것을 돌려줌
  Optional<Merchant> findFirstByOwnerIdOrderByIdAsc(Long ownerId);
}
