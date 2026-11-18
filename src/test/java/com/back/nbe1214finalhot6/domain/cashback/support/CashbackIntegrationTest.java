package com.back.nbe1214finalhot6.domain.cashback.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * cashback 통합 테스트용 공통 설정.
 *
 * <ul>
 *   <li>test 프로필(테스트 전용 DB devpay_test, 스키마 매번 재생성, 스케줄러 끔)
 *   <li>테스트 DB 가 없으면 자동 생성
 *   <li>시간을 옮길 수 있는 {@link MutableClock}
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseInitializer.class)
@Import(TestClockConfig.class)
public @interface CashbackIntegrationTest {}
