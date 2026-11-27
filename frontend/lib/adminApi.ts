import { api, ApiError } from "@/lib/api";
import type { EarningStatus, ExclusionCode, Notice } from "@/lib/cashbackApi";
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

// 모든 이용자의 적립 내역. 이용자는 지갑 번호로만 구분한다
export type AdminEarning = {
  earningId: number; paymentId: number; walletId: number; status: EarningStatus; walletUsedAmount: number; amount: number; appliedRate: number | null;
  requestedAt: string; payableAt: string | null; paidAt: string | null; canceledAt: string | null; exclusionReason: ExclusionCode | null;
};
// 빈 문자열은 조건 없음
export type EarningFilter = { status: EarningStatus | ""; walletId: string; from: string; to: string };
export const getAdminEarnings = (filter: EarningFilter, page = 0, size = 20) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  for (const [key, value] of Object.entries(filter)) if (value) params.set(key, value);
  return api<PageResponse<AdminEarning>>(`${BASE}/earnings?${params}`);
};

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
// 운영자 설정 한도 (월 적립 한도, 지갑 보유·월 충전 한도). 범위와 기본값은 서버가 알려 준다
export type SettingKey = "CASHBACK_USER_MONTHLY_LIMIT" | "WALLET_HOLDING_LIMIT" | "WALLET_MONTHLY_CHARGE_LIMIT";
export type OperatorSetting = {
  key: SettingKey; description: string; value: number; source: "DEFAULT" | "OPERATOR";
  defaultValue: number; minValue: number; hardMax: number | null; updatedAt: string | null;
  scheduled?: { value: number; reset: boolean; effectiveDate: string; noticeId: number | null } | null; // 대기 중인 예약 (#265)
};
export const getSettings = () => api<OperatorSetting[]>("/admin/settings");
// 한도를 바꾸면서 알리는 공지
export type SettingNotice = { title: string; content: string; showOnHome: boolean; homeUntil: string | null };
export const updateSetting = (key: SettingKey, value: number, notice: SettingNotice) => api<OperatorSetting>(`/admin/settings/${key}`, { method: "PUT", body: { value, notice } });
export const resetSetting = (key: SettingKey, notice: SettingNotice) => api<OperatorSetting>(`/admin/settings/${key}`, { method: "DELETE", body: { notice } });
// 적용일 00:00 부터 새 값을 쓴다. value 가 null 이면 기본값으로 되돌리는 예약
export const scheduleSetting = (key: SettingKey, body: { value: number | null; effectiveDate: string; notice: SettingNotice }) => api<OperatorSetting>(`/admin/settings/${key}/schedule`, { method: "POST", body });
export const cancelSchedule = (key: SettingKey) => api<OperatorSetting>(`/admin/settings/${key}/schedule`, { method: "DELETE" });
// 한도 변경 이력 (최근순)
export type SettingHistory = {
  historyId: number; key: SettingKey; description: string; previousValue: number; value: number; reset: boolean; changedAt: string;
};
export const getSettingHistory = (page = 0, size = 10) => api<PageResponse<SettingHistory>>(`/admin/settings/history?page=${page}&size=${size}`);
// 공지 관리: 거둔 공지를 포함한 전체 목록과 수정
export type AdminNotice = { notice: Notice; status: "PUBLISHED" | "WITHDRAWN"; showOnHome: boolean; homeUntil: string | null };
export const getAdminNotices = (page = 0, size = 20) => api<PageResponse<AdminNotice>>(`/admin/notices?page=${page}&size=${size}`);
export const updateNotice = (noticeId: number, body: SettingNotice) => api<AdminNotice>(`/admin/notices/${noticeId}`, { method: "PATCH", body });
// 요율 구간 전체 (지금 적용 중인 구간과 예정 구간을 고른다)
export type Policy = { policyId: number; rate: number; startsAt: string; endsAt: string | null };
export const getPolicies = () => api<Policy[]>(`${BASE}/policies`);
export const getForecast = () => api<Forecast>(`${BASE}/forecast`);
// 하루 한 번 저장한 예측의 하루 예상 수요와 그날 실제 확보액의 비교. 최근 날짜부터
export type ForecastAccuracy = {
  averageError: number | null; withinRangeDays: number;
  days: { date: string; predictedDemand: number; slowDemand: number; fastDemand: number; actualAmount: number; difference: number; withinRange: boolean; modelVersion: string }[];
};
export const getForecastAccuracy = () => api<ForecastAccuracy>(`${BASE}/forecast/accuracy`);
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
