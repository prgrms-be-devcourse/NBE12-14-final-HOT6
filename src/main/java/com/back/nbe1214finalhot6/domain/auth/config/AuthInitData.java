package com.back.nbe1214finalhot6.domain.auth.config;

import com.back.nbe1214finalhot6.domain.auth.entity.User;
import com.back.nbe1214finalhot6.domain.auth.entity.UserRole;
import com.back.nbe1214finalhot6.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 서버가 시작할 때 환경 변수로 받은 값으로 최초 운영자 계정을 한 번 만든다.
 *
 * <p>운영자 계정은 가입 후 DB 에서 역할을 바꾸는 방법밖에 없어서, DB 를 새로 만들 때마다 사람이 해야 했다. 비밀번호는 코드와 저장소에 두지 않고 {@code ADMIN_EMAIL},
 * {@code ADMIN_PASSWORD} 같은 환경 변수로만 받는다. 값이 하나라도 비어 있으면 아무것도 하지 않는다.
 *
 * <p>같은 이메일이나 전화번호의 사용자가 이미 있으면 건너뛰므로 기존 계정의 역할·비밀번호를 바꾸지 않고, 여러 번 시작해도 한 건만 남는다. 운영자는 결제하지 않으므로
 * 이용자 가입과 달리 지갑과 모의계좌는 만들지 않는다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class AuthInitData implements ApplicationRunner {

  private final AuthProperties properties;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public void run(ApplicationArguments args) {
    AuthProperties.Init init = properties.init();
    if (!init.enabled()) {
      return;
    }
    init.validate();

    String email = init.normalizedEmail();
    if (userRepository.existsByEmail(email)) {
      log.info("운영자 계정을 만들지 않았습니다. 같은 이메일의 사용자가 이미 있습니다: {}", email);
      return;
    }
    if (userRepository.existsByPhone(init.adminPhone())) {
      log.warn("운영자 계정을 만들지 않았습니다. 같은 전화번호의 사용자가 이미 있습니다(ADMIN_PHONE 확인)");
      return;
    }

    User admin =
        User.create(
            init.adminName().strip(),
            email,
            init.adminPhone(),
            passwordEncoder.encode(init.adminPassword()),
            passwordEncoder.encode(init.adminPin()),
            UserRole.ADMIN);
    try {
      userRepository.save(admin);
    } catch (DataIntegrityViolationException e) {
      // 서버를 동시에 시작해 다른 쪽이 먼저 만든 경우
      log.warn("운영자 계정을 만들지 않았습니다. 같은 이메일이나 전화번호가 동시에 등록됐습니다: {}", email);
      return;
    }
    log.info("운영자 계정을 만들었습니다: {}", email);
  }
}
