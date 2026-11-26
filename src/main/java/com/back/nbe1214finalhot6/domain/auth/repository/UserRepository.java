package com.back.nbe1214finalhot6.domain.auth.repository;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  boolean existsByPhone(String phone);

  // 주어진 계정 종류 중에 이 이메일·전화번호를 쓰는 계정이 있는지(탈퇴한 계정 포함)
  boolean existsByRoleInAndEmail(Collection<UserRole> roles, String email);

  boolean existsByRoleInAndPhone(Collection<UserRole> roles, String phone);

  // 같은 이메일을 이용자 계정과 점주 계정이 함께 쓸 수 있어 여러 건이 나올 수 있다
  List<User> findAllByEmail(String email);

  // 인증 필터가 매 요청 탈퇴 여부를 확인하는 데 쓴다. 객체를 읽지 않고 존재 여부만 가볍게 묻는다
  boolean existsByIdAndStatus(Long id, UserStatus status);

  /**
   * PIN 실패 횟수를 1 올리고, 올린 값이 한도(max)에 닿으면 같은 문장에서 잠금 시각도 기록한다.
   *
   * <p>읽고 더하는 대신 DB 가 직접 더하고(pin_fail_count + 1), 이미 잠긴 회원은 조건에서 빠지므로(pin_locked_at is null)
   * 동시에 여러 번 틀려도 한도를 넘겨 올라가지 않는다. 반영된 행이 0이면 이미 잠긴 것이다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update User u set u.pinFailCount = u.pinFailCount + 1, "
          + "u.pinLockedAt = case when u.pinFailCount + 1 >= :max then :now else u.pinLockedAt end "
          + "where u.id = :id and u.pinLockedAt is null")
  int recordPinFailure(
      @Param("id") Long id, @Param("max") int max, @Param("now") LocalDateTime now);

  /**
   * 잠긴 지 오래돼서(잠금 시각이 threshold 이전) 잠금 시간이 지난 회원의 잠금과 실패 횟수를 초기화한다.
   *
   * <p>조건에 잠금 시각을 넣어서, 이미 다른 요청이 풀고 그 사이에 새로 틀려 올린 횟수를 되돌리지 않는다. 반영된 행이 0이면 풀 것이 없는 것이다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update User u set u.pinFailCount = 0, u.pinLockedAt = null "
          + "where u.id = :id and u.pinLockedAt is not null and u.pinLockedAt <= :threshold")
  int unlockPinIfExpired(@Param("id") Long id, @Param("threshold") LocalDateTime threshold);

  /** 맞는 PIN 이면 실패 횟수를 0으로 되돌린다. 잠긴 회원은 건드리지 않는다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update User u set u.pinFailCount = 0 where u.id = :id and u.pinLockedAt is null")
  int resetPinFailCount(@Param("id") Long id);

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
