export const BASE_RATE = 10;
export const RESERVE_RATIO = 0.1;
export const STOP_RATIO = 0.05;

export const ANNUAL_BUDGET = 100_000_000;
export const MONTH_LABELS = ["1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"];
export const INITIAL_ALLOCATION = [8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 10, 8].map((m) => m * 1_000_000);

export const ACTIVITY: Record<number, { spent: number; reserved: number }> = {
  9: { spent: 7_100_000, reserved: 0 },
  10: { spent: 6_500_000, reserved: 400_000 },
  11: { spent: 4_200_000, reserved: 1_800_000 },
};

export const NOV_BUDGET = 10_000_000;
export const DAYS_IN_MONTH = 30;
export const TODAY = 21;

const NOV_USED = ACTIVITY[11].spent + ACTIVITY[11].reserved;
const BASE_DAILY = NOV_USED / TODAY;
const ELASTICITY = 0.3;

export const SIM_ASSUMPTIONS = {
  baseRate: BASE_RATE,
  baseDaily: Math.round(BASE_DAILY),
  elasticity: ELASTICITY,
};

export const dailyPayout = (rate: number) =>
  Math.max(0, BASE_DAILY * (rate / BASE_RATE) * (1 + (ELASTICITY * (rate - BASE_RATE)) / BASE_RATE));

export const actualRemaining = (day: number) => NOV_BUDGET - (NOV_USED * day) / TODAY;

export const projectedRemaining = (rate: number, day: number) =>
  Math.max(0, actualRemaining(TODAY) - dailyPayout(rate) * (day - TODAY));

export const remainingAt = (rate: number, day: number) => (day <= TODAY ? actualRemaining(day) : projectedRemaining(rate, day));

export function firstDayBelow(rate: number, threshold: number): number | null {
  const now = actualRemaining(TODAY);
  if (now <= threshold) return TODAY;
  const pay = dailyPayout(rate);
  if (pay <= 0) return null;
  const day = TODAY + Math.ceil((now - threshold) / pay);
  return day > DAYS_IN_MONTH ? null : day;
}

export const manwon = (n: number) => `${Math.round(n / 10_000).toLocaleString("ko-KR")}만`;
export const dayLabel = (day: number) => `11.${day}`;
