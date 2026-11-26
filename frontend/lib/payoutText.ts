// 지급 배치 화면(목록·상세)에서 함께 쓰는 표시
export const TRIGGER_TEXT = { SCHEDULED: "자동", MANUAL: "수동" } as const;
export const payoutDateTime = (iso: string | null) => (iso ? `${iso.slice(5, 7)}.${iso.slice(8, 10)} ${iso.slice(11, 16)}` : "-");
