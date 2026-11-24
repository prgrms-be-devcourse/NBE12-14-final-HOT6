import { api } from "@/lib/api";
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
export type NoticeType = "POLICY_CHANGE" | "BUDGET_NOTICE" | "EMERGENCY_STOP" | "STOP_CANCELED";
export type Notice = {
  noticeId: number; type: NoticeType; content: string; effectiveFrom: string; stopAt: string | null; createdAt: string;
  rate?: number | null; previousRate?: number | null; // 공지 요율 (#161)
};

export const getCashbackSummary = () => api<CashbackSummary>("/cashback/summary");
export const getMonthlyLimit = () => api<MonthlyLimit>("/cashback/monthly-limit");
export const getEarnings = (status?: EarningStatus, page = 0, size = 20) =>
  api<PageResponse<Earning>>(`/cashback/earnings?page=${page}&size=${size}${status ? `&status=${status}` : ""}`);
export const getNotices = (type?: NoticeType, page = 0, size = 20) =>
  api<PageResponse<Notice>>(`/notices?${type ? `type=${type}&` : ""}page=${page}&size=${size}`);

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

// 곧 적용될 요율 인하 예고 (오늘 이후에 시작하는 가장 최근 POLICY_CHANGE 공지)
export async function getUpcomingPolicyChange(): Promise<Notice | null> {
  const today = new Date().toISOString().slice(0, 10);
  const page = await getNotices("POLICY_CHANGE", 0, 5);
  return page.content.find((n) => n.effectiveFrom > today) ?? null;
}
