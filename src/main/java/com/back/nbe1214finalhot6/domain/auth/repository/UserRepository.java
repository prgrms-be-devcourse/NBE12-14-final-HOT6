package com.back.nbe1214finalhot6.domain.auth.repository;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
