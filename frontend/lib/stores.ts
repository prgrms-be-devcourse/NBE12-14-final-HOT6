// TODO: 가맹점 목록 조회 API 가 생기면 교체. 지금은 지도 화면 확인용 목 데이터 + 내가 등록한 가맹점.
export type Category = "식당" | "카페" | "미용실" | "병원" | "마트" | "기타";

export const CATEGORIES: { value: Category; icon: string }[] = [
  { value: "식당", icon: "🍴" },
  { value: "카페", icon: "☕" },
  { value: "미용실", icon: "✂️" },
  { value: "병원", icon: "🏥" },
  { value: "마트", icon: "🛒" },
  { value: "기타", icon: "🏪" },
];
export const iconOf = (c: string) => CATEGORIES.find((x) => x.value === c)?.icon ?? "🏪";

export type Store = {
  id: string;
  name: string;
  category: Category;
  address: string;
  lat: number;
  lng: number;
  eligible: boolean; // 캐시백 적립 대상 가맹점
  open: boolean;
  mine?: boolean;
};

export const STORES: Store[] = [
  { id: "s1", name: "○○카페", category: "카페", address: "서울 중구 세종대로 110", lat: 37.5662, lng: 126.9785, eligible: true, open: true },
  { id: "s2", name: "을지로 한식당", category: "식당", address: "서울 중구 을지로 30", lat: 37.5664, lng: 126.9816, eligible: true, open: true },
  { id: "s3", name: "△△마트", category: "마트", address: "서울 중구 남대문로 52", lat: 37.5636, lng: 126.9779, eligible: true, open: true },
  { id: "s4", name: "헤어숍 모나", category: "미용실", address: "서울 중구 무교로 21", lat: 37.5688, lng: 126.9794, eligible: false, open: true },
  { id: "s5", name: "시청 내과의원", category: "병원", address: "서울 중구 태평로1가 61", lat: 37.5647, lng: 126.9766, eligible: false, open: true },
  { id: "s6", name: "□□서점", category: "기타", address: "서울 종로구 종로 1", lat: 37.5702, lng: 126.9831, eligible: true, open: false },
  { id: "s7", name: "◇◇분식", category: "식당", address: "서울 중구 을지로 15", lat: 37.5671, lng: 126.9772, eligible: true, open: true },
  { id: "s8", name: "광화문 커피랩", category: "카페", address: "서울 종로구 세종대로 175", lat: 37.5718, lng: 126.9768, eligible: true, open: true },
  { id: "s9", name: "미소 치과", category: "병원", address: "서울 중구 소공로 70", lat: 37.5640, lng: 126.9808, eligible: true, open: true },
  { id: "s10", name: "스타일 헤어", category: "미용실", address: "서울 종로구 청계천로 10", lat: 37.5694, lng: 126.9812, eligible: true, open: true },
];
