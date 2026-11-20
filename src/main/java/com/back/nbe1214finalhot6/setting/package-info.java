/**
 * 운영자 설정 저장소.
 *
 * <p>이용자 월 적립 한도, 지갑 보유 한도·월 충전 한도처럼 운영 중에 바꾸는 값을 코드나 yaml 이 아니라 DB 에서 읽는다. 여러 도메인이 함께 쓰므로 특정
 * 도메인 안이 아니라 최상위 패키지에 둔다.
 *
 * <p>경계 규칙
 *
 * <ul>
 *   <li>다른 도메인은 {@code service} 의 {@code OperatorSettingService.getLong(SettingKey)} 로만 읽는다.
 *       이 패키지의 엔티티·리포지토리를 직접 사용하지 않는다.
 *   <li>키마다 기본값, 최솟값, 하드 상한이 {@link com.back.nbe1214finalhot6.setting.SettingKey} 에 코드로 정의된다.
 *       하드 상한은 법적 한도 같은 값이라 운영자가 바꿀 수 없다.
 *   <li>값을 바꾸는 운영자 API 와 변경 이력은 별도 이슈에서 이 패키지에 추가한다.
 * </ul>
 */
package com.back.nbe1214finalhot6.setting;
