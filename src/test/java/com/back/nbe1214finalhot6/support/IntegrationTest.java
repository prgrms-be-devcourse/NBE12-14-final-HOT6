package com.back.nbe1214finalhot6.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 통합 테스트용 공통 설정. 도메인과 무관하게 쓸 수 있다.
 *
 * <ul>
 *   <li>test 프로필(테스트 전용 DB devpay_test, 스키마 매번 재생성)
 *   <li>테스트 DB 가 없으면 자동 생성
 *   <li>시간을 옮길 수 있는 {@link MutableClock}. 매 테스트 전에 시작 시각으로 초기화한다
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseInitializer.class)
@Import(TestClockConfig.class)
@ExtendWith(ClockResetExtension.class)
public @interface IntegrationTest {}
