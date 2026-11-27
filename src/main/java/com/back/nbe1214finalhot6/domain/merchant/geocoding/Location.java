package com.back.nbe1214finalhot6.domain.merchant.geocoding;

/** 가맹점 위치. 범위를 벗어난 값은 만들 수 없다. 네이버 응답의 x 는 경도, y 는 위도이므로 순서를 바꿔 넣지 않는다. */
public record Location(double latitude, double longitude) {

  public Location {
    if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("위도는 -90 이상 90 이하여야 한다: " + latitude);
    }
    if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("경도는 -180 이상 180 이하여야 한다: " + longitude);
    }
  }
}
