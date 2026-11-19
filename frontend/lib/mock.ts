import { LIMITS } from "@/lib/limits";

// TODO: 백엔드 API 연동 시 교체하는 목 데이터

export type TxKind = "charge" | "refund" | "payment" | "cashback";
export type Tx = { id: string; title: string; date: string; amount: number; kind: TxKind; isNew?: boolean };

export const wallet = {
  devMoney: 125000,
  cashbackBalance: 3200,
  monthlyCashback: 3200,
  monthlyLimit: LIMITS.monthlyCashback,
  monthlyCharged: 80000, // 이번 달 충전 누계 (11.10 30,000 + 11.15 50,000)
  pendingCashback: 480,
  paidCashback: 2720,
};

export const walletTxs: Tx[] = [
  { id: "t1", title: "충전", date: "11.15 10:20", amount: 50000, kind: "charge" },
  { id: "t2", title: "결제 · ○○카페", date: "11.14 18:02", amount: -12000, kind: "payment" },
  { id: "t3", title: "환불", date: "11.12 09:40", amount: -10000, kind: "refund" },
  { id: "t4", title: "충전", date: "11.10 20:15", amount: 30000, kind: "charge" },
];

export type Payment = {
  id: string;
  merchant: string;
  date: string;
  amount: number;
  status: "완료" | "취소";
  no: string;
};

export const payments: Payment[] = [
  { id: "p1", merchant: "○○카페", date: "11.17 12:31", amount: 12000, status: "완료", no: "PAY-0001" },
  { id: "p2", merchant: "△△마트", date: "11.16 19:20", amount: 8500, status: "완료", no: "PAY-0002" },
  { id: "p3", merchant: "□□서점", date: "11.14 15:05", amount: 15000, status: "취소", no: "PAY-0003" },
  { id: "p4", merchant: "◇◇분식", date: "11.12 12:10", amount: 6000, status: "완료", no: "PAY-0004" },
];

export type Accrual = {
  id: string;
  merchant: string;
  date: string;
  desc: string;
  amount: number;
  status: "예정" | "지급완료" | "제외";
};

export const accruals: Accrual[] = [
  { id: "a1", merchant: "○○카페", date: "11.17", desc: "결제 12,000원 × 2.0%", amount: 240, status: "예정" },
  { id: "a2", merchant: "△△마트", date: "11.16", desc: "결제 9,000원 × 2.0%", amount: 180, status: "예정" },
  { id: "a3", merchant: "□□서점", date: "11.14", desc: "제외: 예산 소진", amount: 0, status: "제외" },
  { id: "a4", merchant: "○○카페", date: "11.05", desc: "결제 12,000원 × 2.0%", amount: 240, status: "지급완료" },
  { id: "a5", merchant: "◇◇분식", date: "11.03", desc: "결제 6,000원 × 2.0%", amount: 120, status: "지급완료" },
];

export const user = { name: "홍길동", email: "hong@mail.com", maskedEmail: "ab***@gmail.com" };

export const merchant = {
  name: "○○카페",
  address: "서울 ○○구 ○○로 12",
  phone: "02-000-0000",
};

export const merchantPayments = [
  { id: "m1", user: "○○○님", time: "12:31", amount: 12000, status: "완료" },
  { id: "m2", user: "○○○님", time: "12:05", amount: 8500, status: "완료" },
  { id: "m3", user: "○○○님", time: "11:40", amount: 20000, status: "취소" },
  { id: "m4", user: "○○○님", time: "11:12", amount: 6000, status: "완료" },
  { id: "m5", user: "○○○님", time: "10:48", amount: 15000, status: "완료" },
] as const;

export const dailySettles = [
  { date: "11.17", net: 320000, carry: 0 },
  { date: "11.16", net: 410000, carry: 0 },
  { date: "11.15", net: 285000, carry: 12000 },
  { date: "11.14", net: 0, carry: 0 },
  { date: "11.13", net: 198000, carry: 0 },
  { date: "11.12", net: 356000, carry: 0 },
];

export const budgetRows = [
  { month: "9월", assigned: "800만", spent: "710만", reserved: "0", left: "90만" },
  { month: "10월", assigned: "800만", spent: "650만", reserved: "40만", left: "110만" },
  { month: "11월", assigned: "800만", spent: "420만", reserved: "180만", left: "200만" },
  { month: "12월", assigned: "800만", spent: "-", reserved: "-", left: "800만" },
];

export const policyHistory = [
  { date: "11.01", rate: "2.0% → 1.5%", period: "12.01 ~", note: "예고 발송" },
  { date: "10.01", rate: "1.5% → 2.0%", period: "10.01 ~ 11.30", note: "시행" },
  { date: "09.01", rate: "2.5% → 1.5%", period: "09.01 ~ 09.30", note: "시행" },
  { date: "08.01", rate: "신규 2.5%", period: "08.01 ~ 08.31", note: "시행" },
];

export const weekdays = ["월", "화", "수", "목", "금", "토", "일"];
