import { api } from "@/lib/api";
import { won } from "@/lib/format";
import type { PageResponse } from "@/lib/types";

// 백엔드 캐시백 응답 (이용자용). 지갑은 서버가 토큰의 회원으로 찾는다
export type CashbackSummary = {
  year: number; month: number; earnedAmount: number; pendingAmount: number; paidAmount: number; excludedCount: number;
};
export type MonthlyLimit = { year: number; month: number; limit: number; usedAmount: number; remaining: number };
export type EarningStatus = "PENDING" | "PAID" | "CANCELED" | "CLAWED_BACK" | "EXCLUDED";
export type ExclusionCode =
  | "NONE" | "NOT_ELIGIBLE" | "NO_WALLET_USAGE" | "AMOUNT_TOO_SMALL" | "MONTHLY_LIMIT_EXCEEDED" | "BUDGET_EXHAUSTED";
export type Earning = {
  earningId: number; paymentId: number; status: EarningStatus; amount: number; appliedRate: number | null;
  requestedAt: string; payableAt: string | null; paidAt: string | null; canceledAt: string | null;
  exclusionReason: ExclusionCode | null;
};
export type NoticeType = "POLICY_CHANGE" | "BUDGET_NOTICE" | "EMERGENCY_STOP" | "STOP_CANCELED" | "LIMIT_CHANGE";
export type Notice = {
  noticeId: number; type: NoticeType; title: string; content: string; effectiveFrom: string; stopAt: string | null; createdAt: string;
  rate?: number | null; previousRate?: number | null; // 공지 요율 (#161)
  settingKey?: string | null; previousValue?: number | null; newValue?: number | null; // 한도 변경 공지 (#265)
  updatedAt?: string | null; // 운영자가 문구를 고친 시각
};

export const getCashbackSummary = () => api<CashbackSummary>("/cashback/summary");
export type PayoutRules = { payoutDelayDays: number };
export const getPayoutRules = () => api<PayoutRules>("/cashback/rules");
export const getMonthlyLimit = () => api<MonthlyLimit>("/cashback/monthly-limit");
export const getEarnings = (status?: EarningStatus, page = 0, size = 20) =>
  api<PageResponse<Earning>>(`/cashback/earnings?page=${page}&size=${size}${status ? `&status=${status}` : ""}`);
export const getNotices = (type?: NoticeType, page = 0, size = 20) =>
  api<PageResponse<Notice>>(`/notices?${type ? `type=${type}&` : ""}page=${page}&size=${size}`);
export const NOTICE_TITLE_MAX = 100;
export const NOTICE_CONTENT_MAX = 500;
// 홈에 띄울 공지 (최신순, 서버가 5건까지 준다)
export const getHomeNotices = () => api<Notice[]>("/notices/home");

export const NOTICE_LABEL: Record<NoticeType, string> = {
  POLICY_CHANGE: "정책 변경", BUDGET_NOTICE: "소진 예고", EMERGENCY_STOP: "적립 중단", STOP_CANCELED: "중단 취소", LIMIT_CHANGE: "한도 변경",
};
export const NOTICE_BADGE: Record<NoticeType, string> = {
  POLICY_CHANGE: "badge-warn", BUDGET_NOTICE: "badge-warn", EMERGENCY_STOP: "badge-bad", STOP_CANCELED: "badge-ok", LIMIT_CHANGE: "badge-info",
};
export const LIMIT_NAME: Record<string, string> = {
  CASHBACK_USER_MONTHLY_LIMIT: "월 적립 한도", WALLET_HOLDING_LIMIT: "지갑 보유 한도", WALLET_MONTHLY_CHARGE_LIMIT: "월 충전 한도",
};

// 공지가 알리는 값의 변화(요율 또는 한도). 변화가 없는 공지는 null
export function noticeChange(n: Notice): { name: string; from: string; to: string } | null {
  if (n.rate != null && n.previousRate != null) return { name: "적립률", from: pctText(n.previousRate), to: pctText(n.rate) };
  if (n.previousValue != null && n.newValue != null) {
    return { name: LIMIT_NAME[n.settingKey ?? ""] ?? "한도", from: won(n.previousValue), to: won(n.newValue) };
  }
  return null;
}

// 화면 표시용 라벨
export const STATUS_LABEL: Record<EarningStatus, string> = {
  PENDING: "예정", PAID: "지급완료", EXCLUDED: "제외", CANCELED: "취소", CLAWED_BACK: "회수",
};
export const STATUS_BADGE: Record<EarningStatus, string> = {
  PENDING: "badge-warn", PAID: "badge-ok", EXCLUDED: "badge-bad", CANCELED: "badge-bad", CLAWED_BACK: "badge-bad",
};
export const EXCLUSION_TEXT: Record<ExclusionCode, string> = {
  NONE: "", NOT_ELIGIBLE: "적립 대상 가맹점 아님", NO_WALLET_USAGE: "데브머니 사용분 없음",
  AMOUNT_TOO_SMALL: "적립액 1원 미만", MONTHLY_LIMIT_EXCEEDED: "월 적립 한도 초과", BUDGET_EXHAUSTED: "이번 달 예산 소진",
};
export const pctText = (rate: number | null | undefined) => (rate == null ? "" : `${Math.round(rate * 10000) / 100}%`);
export const mmdd = (iso: string | null) => (iso ? `${iso.slice(5, 7)}.${iso.slice(8, 10)}` : "");

// 결제 전 미리보기 (예상값). 확정은 결제 응답·영수증이 기준이다
export type CashbackPreview = {
  earnable: boolean; paused: boolean; appliedRate: number; expectedAmount: number; monthlyLimitRemaining: number; exclusionReason: ExclusionCode;
  limitReduced: boolean; // 남은 월 한도가 모자라 예상 적립액을 남은 한도만큼으로 줄였는지
};
export const getCashbackPreview = (merchantId: number, walletUsedAmount: number) =>
  api<CashbackPreview>(`/cashback/preview?merchantId=${merchantId}&walletUsedAmount=${walletUsedAmount}`);

// 적립 제외 사유 안내 문구 (미리보기·영수증 공통)
export const REASON_TEXT: Record<ExclusionCode, string> = {
  NONE: "적립됩니다",
  NOT_ELIGIBLE: "적립 대상 가맹점이 아니에요. 캐시백 잔액은 이 매장에서도 사용할 수 있어요.",
  NO_WALLET_USAGE: "데브머니로 결제한 금액이 없어 적립되지 않아요.",
  AMOUNT_TOO_SMALL: "적립액이 1원 미만이라 적립되지 않아요.",
  MONTHLY_LIMIT_EXCEEDED: "이번 달 적립 한도를 넘어 적립되지 않아요.",
  BUDGET_EXHAUSTED: "이번 달 캐시백 예산이 소진되었거나 적립이 중단되어 적립되지 않아요.",
};

// 사유 안내의 색: 적립됨=초록, 대상 아님·데브머니 없음·1원 미만=회색, 월 한도 초과=주황, 예산 소진·적립 중단=빨강
export type ReasonTone = "ok" | "muted" | "warn" | "bad";
export const REASON_TONE: Record<ExclusionCode, ReasonTone> = {
  NONE: "ok", NOT_ELIGIBLE: "muted", NO_WALLET_USAGE: "muted", AMOUNT_TOO_SMALL: "muted",
  MONTHLY_LIMIT_EXCEEDED: "warn", BUDGET_EXHAUSTED: "bad",
};
export const REASON_TONE_CLASS: Record<ReasonTone, string> = {
  ok: "border-emerald-200 bg-emerald-50 text-emerald-800",
  muted: "border-slate-200 bg-slate-50 text-slate-600",
  warn: "border-orange-200 bg-orange-50 text-orange-800",
  bad: "border-red-200 bg-red-50 text-red-700",
};
