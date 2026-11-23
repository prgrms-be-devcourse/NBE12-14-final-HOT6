package com.back.nbe1214finalhot6.domain.auth.repository;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  boolean existsByPhone(String phone);

  Optional<User> findByEmail(String email);

  /**
   * 활동 중인 회원을 탈퇴로 바꾼다. 객체를 읽어 고쳐 저장하면 그 사이 다른 요청(예: PIN 실패 횟수)이 바꾼 값을 낡은 값으로 덮어쓸 수 있어서, 상태와 탈퇴 시각만
   * 조건부 UPDATE 로 바꾼다. 반영된 행이 0이면 이미 탈퇴한 것이다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update User u set u.status = :withdrawn, u.withdrawnAt = :now "
          + "where u.id = :id and u.status = :active")
  int markWithdrawn(
      @Param("id") Long id,
      @Param("now") LocalDateTime now,
      @Param("active") UserStatus active,
      @Param("withdrawn") UserStatus withdrawn);
}
