package com.back.nbe1214finalhot6.domain.merchant.repository;

import com.back.nbe1214finalhot6.domain.merchant.entity.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

  Page<Merchant> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
