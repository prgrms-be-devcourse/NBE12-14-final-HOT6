package com.back.nbe1214finalhot6.domain.auth.service;

import com.back.nbe1214finalhot6.common.exception.BusinessException;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupRequest;
import com.back.nbe1214finalhot6.domain.auth.dto.SignupResponse;
import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.exception.AuthErrorCode;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public SignupResponse signup(SignupRequest request) {
    // 1. 이메일 중복이면 예외
    if (userRepository.existsByEmail(request.email())) {
      throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
    }
    // 2. 전화번호 중복이면 예외
    if (userRepository.existsByPhone(request.phone())) {
      throw new BusinessException(AuthErrorCode.DUPLICATE_PHONE);
    }
    // 3. 암호화
    String passwordHash = passwordEncoder.encode(request.password());
    String pinHash = passwordEncoder.encode(request.pin());

    // 4. 엔티티 생성
    User user =
        User.create(
            request.name(), request.email(), request.phone(), passwordHash, pinHash, UserRole.USER);

    // 5. 저장
    User saved = userRepository.save(user);

    // 6. 응답으로 변환
    return SignupResponse.from(saved);
  }
}
