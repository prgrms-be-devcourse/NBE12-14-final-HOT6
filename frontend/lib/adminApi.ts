import { api, ApiError } from "@/lib/api";
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

// 서버 설정값(운영 화면의 안내 문구가 쓴다)
export type CashbackRules = { contingencyRate: number; emergencyRatio: number; noticeDays: number; payoutDelayDays: number };
export const getCashbackRules = () => api<CashbackRules>(`${BASE}/rules`);
export const getMonthlyBudget = (year: number, month: number) => api<MonthlyBudget>(`${BASE}/budget/monthly/${year}/${month}`);
export const getPolicyHistory = (page = 0, size = 10) => api<PageResponse<PolicyHistory>>(`${BASE}/policies/history?page=${page}&size=${size}`);
// 적립 지급 배치: 대기 현황, 수동 실행, 실행 목록과 상세
export type PayoutRunResult = { paid: number; skipped: number; failed: number };
export type PayoutStatusData = {
  pendingCount: number; pendingAmount: number; dueCount: number; dueAmount: number; nextPayableAt: string | null;
};
export type PayoutRunSummary = PayoutRunResult & { runId: number; ranAt: string; trigger: "SCHEDULED" | "MANUAL" };
export type PayoutItemResult = "PAID" | "SKIPPED" | "FAILED";
export type PayoutRunDetail = {
  run: PayoutRunSummary; paidAmount: number;
  items: { earningId: number; paymentId: number; walletId: number; amount: number; result: PayoutItemResult; errorCode: string | null; errorMessage: string | null }[];
};
export const getPayoutStatus = () => api<PayoutStatusData>(`${BASE}/payouts/status`);
export const getPayoutRuns = (page = 0, size = 20) => api<PageResponse<PayoutRunSummary>>(`${BASE}/payouts/runs?page=${page}&size=${size}`);
export const getPayoutRun = (runId: number) => api<PayoutRunDetail>(`${BASE}/payouts/runs/${runId}`);
export const runPayouts = () => api<PayoutRunResult>(`${BASE}/payouts/run`, { method: "POST" });
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
// 그래프 금액 단위. 1만 원에 못 미치는 작은 예산도 보이도록, 가장 큰 값이 1만 원 미만이면 원 단위로 쓴다
export const moneyUnit = (max: number) => (max >= 10_000 ? { label: "만원", div: 10_000 } : { label: "원", div: 1 });
export const md = (iso: string | null) => (iso ? `${iso.slice(5, 7)}.${iso.slice(8, 10)}` : "-");

// 연·월 예산 (/api/admin/cashback/budget/**)
export type AnnualBudget = {
  budgetYear: number; totalAmount: number; carriedInAmount: number; carriedOutAmount: number;
  reservedAmount: number; paidAmount: number; remainingAmount: number;
};
export type BudgetTimePoint = { date: string; paidAmount: number; reservedAmount: number; remainingAmount: number };
export type BudgetMonthSeries = { year: number; month: number; allocatedAmount: number; contingencyAmount: number; points: BudgetTimePoint[] };

// 아직 등록하지 않은 연도(404)는 오류가 아니라 null 로 돌려준다
export const getAnnualBudget = (year: number) =>
  api<AnnualBudget>(`${BASE}/budget/annual/${year}`).catch((e: unknown) => {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  });
export const putAnnualBudget = (year: number, totalAmount: number) =>
  api<AnnualBudget>(`${BASE}/budget/annual/${year}`, { method: "PUT", body: { totalAmount } });
export const listMonthlyBudgets = (year: number) => api<MonthlyBudget[]>(`${BASE}/budget/monthly?year=${year}`);
// baseRate 는 소수 비율(0.1 = 10%). 비우면 서버가 기존 값이나 이전 달 값을 이어받는다
export const putMonthlyBudget = (year: number, month: number, allocatedAmount: number, baseRate?: number) =>
  api<MonthlyBudget>(`${BASE}/budget/monthly/${year}/${month}`, { method: "PUT", body: { allocatedAmount, ...(baseRate === undefined ? {} : { baseRate }) } });
export const transferBudget = (year: number, month: number, amount: number) =>
  api<unknown>(`${BASE}/budget/transfers`, { method: "POST", body: { year, month, amount } });
export const getBudgetSeries = (from: string, to: string) =>
  api<BudgetMonthSeries[]>(`${BASE}/budget/timeseries?from=${from}&to=${to}`);
