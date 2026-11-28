package com.back.nbe1214finalhot6.domain.cashback.dto;

/**
 * 이번 달 월 적립 한도 사용률의 분포. 이번 달에 적립한(사용량이 있는) 이용자 수이며 이용자를 알 수 있는 값은 담지 않는다.
 *
 * @param limit 지금 적용 중인 월 적립 한도(원)
 * @param userCount 이번 달에 적립한 이용자 수
 * @param below25 한도의 25% 미만을 쓴 이용자 수
 * @param below50 25% 이상 50% 미만
 * @param below75 50% 이상 75% 미만
 * @param below100 75% 이상 100% 미만
 * @param reached 한도에 닿았거나 넘은 이용자 수. 한도를 낮추면 넘은 이용자가 생길 수 있다
 */
public record MonthlyLimitUsageResponse(
    int year,
    int month,
    long limit,
    long userCount,
    long below25,
    long below50,
    long below75,
    long below100,
    long reached) {}
