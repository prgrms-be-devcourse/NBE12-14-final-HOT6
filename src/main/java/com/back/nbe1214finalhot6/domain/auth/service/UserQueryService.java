package com.back.nbe1214finalhot6.domain.auth.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.common.response.CommonErrorCode;
import com.back.nbe1214finalhot6.domain.auth.dto.MyInfoResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.entity.UserStatus;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 정보 조회. */
@Service
@RequiredArgsConstructor
public class UserQueryService {

  private final UserRepository userRepository;
  private final Clock clock;

  /**
   * 로그인한 회원의 정보를 DB의 현재 값으로 돌려준다.
   *
   * <p>토큰에는 로그인 시점의 역할이 들어 있어서 승격 직후에는 옛 값이다. 여기서는 DB의 현재 역할을 주고, 토큰의 역할(tokenRole)과 다르면 needsRelogin 을 true 로 알려 준다.
   * 없는 회원이거나 탈퇴한 회원이면 COM-009(404)이다. 탈퇴해도 토큰은 만료(30분)까지 유효하기 때문이다.
   */
  @Transactional(readOnly = true)
  public MyInfoResponse getMyInfo(Long userId, UserRole tokenRole) {
    return userRepository
        .findById(userId)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .map(user -> MyInfoResponse.from(user, tokenRole, LocalDateTime.now(clock)))
        .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
  }
}
