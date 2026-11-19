import { wallet } from "@/lib/mock";

// 백엔드 ExclusionReason 과 동일한 코드
export type ExclusionReason = "NONE" | "NOT_ELIGIBLE" | "BUDGET_EXHAUSTED" | "MONTHLY_LIMIT_EXCEEDED";

export const REASON_TEXT: Record<ExclusionReason, string> = {
  NONE: "적립됩니다",
  NOT_ELIGIBLE: "적립 대상 가맹점이 아니에요. 캐시백 잔액은 이 매장에서도 사용할 수 있어요.",
  BUDGET_EXHAUSTED: "이번 달 캐시백 예산이 소진되어 적립되지 않아요.",
  MONTHLY_LIMIT_EXCEEDED: "이번 달 적립 한도를 넘어 적립되지 않아요.",
};

// 결제 전 미리보기를 데모하기 위한 상황 (T1 미리보기 API 연동 전)
export type Scenario = "normal" | "notEligible" | "budgetExhausted" | "limitExceeded" | "paused";
export const SCENARIOS: { value: Scenario; label: string }[] = [
  { value: "normal", label: "정상 적립" },
  { value: "notEligible", label: "적립 제외 가맹점" },
  { value: "budgetExhausted", label: "예산 소진" },
  { value: "limitExceeded", label: "월 한도 초과" },
  { value: "paused", label: "적립 중단 중" },
];

export type Preview = {
  eligible: boolean;
  paused: boolean;
  rate: number; // %
  expected: number;
  monthlyRemaining: number;
  reason: ExclusionReason;
  upcoming: { rate: number; from: string } | null;
};

export function computePreview(amount: number, scenario: Scenario): Preview {
  const rate = 2.0;
  const eligible = scenario !== "notEligible";
  const paused = scenario === "paused";
  const monthlyRemaining = scenario === "limitExceeded" ? 0 : wallet.monthlyLimit - wallet.monthlyCashback;
  const raw = Math.floor((amount * rate) / 100); // 정수 버림

  let reason: ExclusionReason = "NONE";
  if (!eligible) reason = "NOT_ELIGIBLE";
  else if (paused || scenario === "budgetExhausted") reason = "BUDGET_EXHAUSTED";
  else if (raw > monthlyRemaining) reason = "MONTHLY_LIMIT_EXCEEDED";

  return {
    eligible, paused, rate,
    expected: reason === "NONE" ? raw : 0,
    monthlyRemaining,
    reason,
    upcoming: { rate: 1.5, from: "12월 1일" },
  };
}
