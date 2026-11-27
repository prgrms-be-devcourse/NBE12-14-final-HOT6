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
 * 회원의 역할을 확인한다.
 *
 * <p>호출한 쪽의 트랜잭션에 참여한다. 토큰의 역할은 로그인 시점의 값이라, DB 의 현재 상태로 확인해야 하는 일에 쓴다.
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

  private final UserRepository userRepository;

  /** 활동 중인 점주(OWNER)인지 확인한다. 없거나 탈퇴한 회원이면 COM-009, 점주가 아니면 COM-008 이다. */
  @Transactional(readOnly = true)
  public void requireOwner(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (user.getRole() != UserRole.OWNER) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
  }
}
