package com.back.nbe1214finalhot6.domain.merchant.geocoding;

/** 주소를 좌표로 바꾸는 외부 서비스. 구현은 예외를 던지지 않고 결과의 상태로 알린다. */
public interface GeocodingClient {

  GeocodingResult geocode(String address);
}
