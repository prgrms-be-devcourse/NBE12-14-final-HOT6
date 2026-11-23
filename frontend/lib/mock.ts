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
  { id: "a1", merchant: "○○카페", date: "11.17", desc: "결제 12,000원 × 10%", amount: 1200, status: "예정" },
  { id: "a2", merchant: "△△마트", date: "11.16", desc: "결제 9,000원 × 10%", amount: 900, status: "예정" },
  { id: "a3", merchant: "□□서점", date: "11.14", desc: "제외: 예산 소진", amount: 0, status: "제외" },
  { id: "a4", merchant: "○○카페", date: "11.05", desc: "결제 12,000원 × 10%", amount: 1200, status: "지급완료" },
  { id: "a5", merchant: "◇◇분식", date: "11.03", desc: "결제 6,000원 × 10%", amount: 600, status: "지급완료" },
];

export const user = { name: "홍길동", email: "hong@mail.com", maskedEmail: "ab***@gmail.com" };

export const policyHistory = [
  { date: "11.01", rate: "10% → 5%", period: "12.01 ~", note: "예고 발송" },
  { date: "10.01", rate: "5% → 10%", period: "10.01 ~ 11.30", note: "시행" },
  { date: "09.01", rate: "15% → 5%", period: "09.01 ~ 09.30", note: "시행" },
  { date: "08.01", rate: "신규 15%", period: "08.01 ~ 08.31", note: "시행" },
];

export const weekdays = ["월", "화", "수", "목", "금", "토", "일"];
