package com.back.nbe1214finalhot6.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 매 테스트 전에 {@link MutableClock}을 시작 시각으로 되돌린다.
 *
 * <p>스프링 컨텍스트는 테스트 클래스끼리 공유되므로, 앞선 테스트가 옮긴 시계(또는 assert 실패로 되돌리지 못한 시계)가 다음 테스트에 남지 않게 한다.
 */
public class ClockResetExtension implements BeforeEachCallback {

  @Override
  public void beforeEach(ExtensionContext context) {
    SpringExtension.getApplicationContext(context).getBean(MutableClock.class).reset();
  }
}
