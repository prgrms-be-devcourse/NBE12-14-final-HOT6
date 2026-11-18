package com.back.nbe1214finalhot6.domain.auth.repository;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
