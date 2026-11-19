/**
 * 캐시백·예산·정책 도메인.
 *
 * <p>구조는 다른 도메인과 같은 레이어드({@code controller, dto, entity, repository, service})다.
 *
 * <p>경계 규칙
 *
 * <ul>
 *   <li>다른 도메인은 {@code service} 의 {@code CashbackService}, {@code CashbackQueryService} 로만 접근한다.
 *       이 도메인의 엔티티·리포지토리를 직접 사용하지 않는다.
 *   <li>다른 도메인의 엔티티는 JPA 연관관계 없이 ID({@code Long})로만 참조한다.
 *   <li>{@code dto} 의 {@code CashbackReserveRequest}, {@code CashbackReserveResponse},
 *       {@code CashbackExclusionReason} 은 다른 도메인과 주고받는 계약 타입이다.
 *       엔티티를 대신 내보내지 않는다.
 * </ul>
 */
package com.back.nbe1214finalhot6.domain.cashback;
