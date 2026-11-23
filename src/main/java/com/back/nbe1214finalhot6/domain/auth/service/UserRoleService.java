package com.back.nbe1214finalhot6.domain.auth.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 역할을 바꾼다.
 *
 * <p>호출한 쪽의 트랜잭션에 참여한다(가맹점 등록과 점주 승격이 함께 성공하거나 함께 취소되도록). 역할은 토큰에 로그인(또는 재발급) 시점의 값이 들어가므로, 승격한
 * 뒤에는 새 토큰을 받아야 새 역할이 적용된다.
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

  private final UserRepository userRepository;

  /** 사용자를 점주(OWNER)로 올린다. 이미 점주면 아무 일도 하지 않는다. */
  @Transactional
  public void promoteToOwner(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (user.getRole() == UserRole.ADMIN) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN); // 운영자는 가맹점을 등록할 수 없다
    }
    user.promoteToOwner();
  }
}
