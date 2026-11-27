package com.back.nbe1214finalhot6.domain.merchant.geocoding;

/**
 * 가맹점 수정에서 좌표를 어떻게 할지. 기존 값 유지, 새 값 저장, 비우기 중 하나다.
 *
 * <p>{@code basedOnAddress}는 이 결정을 내릴 때 기준으로 삼은 주소다. 읽기와 저장이 서로 다른 트랜잭션이라 그 사이에 주소가 바뀔 수 있으므로, 저장할 때 지금 주소와 같을
 * 때만 반영한다.
 */
public record LocationChange(boolean apply, Location location, String basedOnAddress) {

  private static final LocationChange KEEP = new LocationChange(false, null, null);

  public static LocationChange keep() {
    return KEEP;
  }

  public static LocationChange set(Location location, String basedOnAddress) {
    return new LocationChange(true, location, basedOnAddress);
  }

  public static LocationChange clear(String basedOnAddress) {
    return new LocationChange(true, null, basedOnAddress);
  }

  /** 저장된 주소가 이 결정의 기준 주소와 같아서 좌표를 반영해도 되는지 */
  public boolean appliesTo(String currentAddress) {
    return apply && basedOnAddress != null && basedOnAddress.equals(currentAddress.strip());
  }
}
