// 네이버 지도(Dynamic Map) 스크립트를 한 번만 불러온다. Client ID 는 브라우저에 보이는 값이고, 허용된 웹 서비스 URL 에서만 동작한다.
// 주소를 좌표로 바꾸는 일은 서버가 한다(Client Secret 은 서버 환경 변수에만 있다).
export type NaverLatLng = object;
export type NaverMapInstance = { fitBounds: (bounds: object, padding?: object) => void; destroy: () => void };
export type NaverMaps = {
  Map: new (el: HTMLElement, options: object) => NaverMapInstance;
  LatLng: new (lat: number, lng: number) => NaverLatLng;
  LatLngBounds: new () => { extend: (p: NaverLatLng) => void };
  Marker: new (options: object) => { setMap: (map: NaverMapInstance | null) => void };
};

declare global {
  interface Window {
    naver?: { maps?: NaverMaps };
    navermap_authFailure?: () => void;
  }
}

export const NAVER_AUTH_FAILURE_EVENT = "navermap-auth-failure";
const CLIENT_ID = process.env.NEXT_PUBLIC_NAVER_MAP_CLIENT_ID ?? "";

export const naverMapConfigured = CLIENT_ID !== "";

let loading: Promise<NaverMaps> | null = null;

export function loadNaverMaps(): Promise<NaverMaps> {
  if (!CLIENT_ID) return Promise.reject(new Error("NEXT_PUBLIC_NAVER_MAP_CLIENT_ID 가 비어 있어요"));
  if (window.naver?.maps) return Promise.resolve(window.naver.maps);
  loading ??= new Promise<NaverMaps>((resolve, reject) => {
    // 키나 허용 URL 이 맞지 않으면 스크립트는 불러와져도 인증에서 실패한다
    window.navermap_authFailure = () => window.dispatchEvent(new Event(NAVER_AUTH_FAILURE_EVENT));
    const script = document.createElement("script");
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(CLIENT_ID)}`;
    script.async = true;
    script.onload = () => {
      const maps = window.naver?.maps;
      if (maps) resolve(maps);
      else reject(new Error("네이버 지도를 불러오지 못했어요"));
    };
    script.onerror = () => reject(new Error("네이버 지도를 불러오지 못했어요"));
    document.head.appendChild(script);
  }).catch((e) => {
    loading = null; // 다음에 다시 시도할 수 있게 한다
    throw e;
  });
  return loading;
}
