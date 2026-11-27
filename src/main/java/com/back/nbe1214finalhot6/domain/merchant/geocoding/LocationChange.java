package com.back.nbe1214finalhot6.domain.merchant.geocoding;

/** 가맹점 수정에서 좌표를 어떻게 할지. 기존 값 유지, 새 값 저장, 비우기 중 하나다. */
public record LocationChange(boolean apply, Location location) {

  private static final LocationChange KEEP = new LocationChange(false, null);
  private static final LocationChange CLEAR = new LocationChange(true, null);

  public static LocationChange keep() {
    return KEEP;
  }

  public static LocationChange set(Location location) {
    return new LocationChange(true, location);
  }

  public static LocationChange clear() {
    return CLEAR;
  }
}
