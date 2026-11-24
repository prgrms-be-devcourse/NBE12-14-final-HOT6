// 가맹점 등록 화면의 업종 선택지. 업종은 서버에 저장하지 않고 이 브라우저에만 저장한다.
export type Category = "식당" | "카페" | "미용실" | "병원" | "마트" | "기타";

export const CATEGORIES: { value: Category; icon: string }[] = [
  { value: "식당", icon: "🍴" },
  { value: "카페", icon: "☕" },
  { value: "미용실", icon: "✂️" },
  { value: "병원", icon: "🏥" },
  { value: "마트", icon: "🛒" },
  { value: "기타", icon: "🏪" },
];
