import { api } from "@/lib/api";
import type { PageResponse } from "@/lib/types";

// 운영자용 캐시백 API (/api/admin/cashback/**)
export type BudgetWarning = "NONE" | "NOTICE_LINE" | "EMERGENCY_LINE";
export type BudgetStatus = "ACTIVE" | "NOTICED" | "STOPPED" | "CLOSED";
export type MonthlyBudget = {
  budgetYear: number; budgetMonth: number; allocatedAmount: number; carriedInAmount: number; carriedOutAmount: number;
  reservedAmount: number; paidAmount: number; contingencyAmount: number; remainingAmount: number;
  usedRate: number; remainingRate: number; warning: BudgetWarning; baseRate: number | null; status: BudgetStatus;
};
export type PolicyHistory = {
  historyId: number; policyId: number; action: "CREATE" | "UPDATE"; changedBy: number; changedAt: string;
  beforeRate: number | null; beforeStartsAt: string | null; beforeEndsAt: string | null;
  afterRate: number | null; afterStartsAt: string | null; afterEndsAt: string | null;
};
export type ForecastScenario = { dailyDemand: number; exhaustionDate: string | null; noticeLineDate: string | null };
export type Forecast = {
  year: number; month: number; remainingAmount: number; contingencyAmount: number; guaranteedUsers: number;
  assumptions: { demandBasis: string; baseDailyDemand: number; demandMultiplier: number; rangeRatio: number; modelVersion: string; asOf: string };
  fast: ForecastScenario; mid: ForecastScenario; slow: ForecastScenario;
  points: { date: string; fastRemaining: number; midRemaining: number; slowRemaining: number }[];
};

const BASE = "/admin/cashback";
export const getMonthlyBudget = (year: number, month: number) => api<MonthlyBudget>(`${BASE}/budget/monthly/${year}/${month}`);
export const getPolicyHistory = (page = 0, size = 10) => api<PageResponse<PolicyHistory>>(`${BASE}/policies/history?page=${page}&size=${size}`);
export const getForecast = () => api<Forecast>(`${BASE}/forecast`);
export const reduceRate = (rate: number, effectiveAt: string) =>
  api<unknown>(`${BASE}/policies/reductions`, { method: "POST", body: { rate, effectiveAt } });
export const noticeStop = (year: number, month: number) => api<MonthlyBudget>(`${BASE}/budget/monthly/${year}/${month}/notice-stop`, { method: "POST" });
export const emergencyStop = (year: number, month: number) =>
  api<MonthlyBudget>(`${BASE}/budget/monthly/${year}/${month}/emergency-stop`, { method: "POST", body: { confirm: true } });

export const thisYearMonth = () => {
  const d = new Date();
  return { year: d.getFullYear(), month: d.getMonth() + 1 };
};
export const STATUS_TEXT: Record<BudgetStatus, string> = { ACTIVE: "정상 적립", NOTICED: "중단 공지 중", STOPPED: "적립 중단", CLOSED: "마감" };
export const manwon = (n: number) => Math.round(n / 10_000);
export const md = (iso: string | null) => (iso ? `${iso.slice(5, 7)}.${iso.slice(8, 10)}` : "-");
