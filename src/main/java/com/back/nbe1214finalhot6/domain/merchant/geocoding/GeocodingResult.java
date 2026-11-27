package com.back.nbe1214finalhot6.domain.merchant.geocoding;

/**
 * 주소를 좌표로 바꾼 결과.
 *
 * <ul>
 *   <li>{@code FOUND}: 좌표를 찾았다
 *   <li>{@code NOT_FOUND}: 외부 서비스가 정상으로 답했지만 그 주소를 찾지 못했다
 *   <li>{@code UNAVAILABLE}: 설정이 비어 있거나 외부 서비스가 실패해서 알 수 없다(시간 초과, 장애, 키 오류)
 * </ul>
 */
public record GeocodingResult(Status status, Location location) {

  public enum Status {
    FOUND,
    NOT_FOUND,
    UNAVAILABLE
  }

  public static GeocodingResult found(Location location) {
    return new GeocodingResult(Status.FOUND, location);
  }

  public static GeocodingResult notFound() {
    return new GeocodingResult(Status.NOT_FOUND, null);
  }

  public static GeocodingResult unavailable() {
    return new GeocodingResult(Status.UNAVAILABLE, null);
  }
}
