import { api } from "@/lib/api";
import type { PageResponse } from "@/lib/types";

// 지갑 거래 내역·충전·환불. 지갑은 서버가 토큰의 회원으로 찾는다 (내 지갑 조회는 endpoints.getMyWallet)
export type WalletTxType =
  | "CHARGE" | "REFUND" | "PAYMENT_DEVMONEY" | "PAYMENT_CASHBACK"
  | "PAYMENT_CANCEL_DEVMONEY" | "PAYMENT_CANCEL_CASHBACK" | "CASHBACK_EARN" | "CASHBACK_CLAWBACK";

// 금액은 입금이 +, 차감이 -. cashback 이 true 면 캐시백 잔액을 바꾼 거래다
export type WalletTx = {
  transactionId: number; type: WalletTxType; amount: number; balanceAfter: number; cashback: boolean;
  refKey: number | null; paymentId: number | null; createdAt: string;
};
export type MoneyResult = { amount: number; createdAt: string };

export const getWalletTransactions = (page = 0, size = 20) =>
  api<PageResponse<WalletTx>>(`/wallet/transactions?page=${page}&size=${size}`);

// 같은 멱등키로 다시 보내면 한 번만 반영하고 처음 결과를 돌려준다. 금액이 다르면 거절(COM-003)되므로 금액이 바뀌면 새 키를 쓴다
export const chargeWallet = (amount: number, idempotencyKey: string) =>
  api<MoneyResult>("/wallet/charges", { method: "POST", body: { amount }, idempotencyKey });
export const refundWallet = (amount: number, idempotencyKey: string) =>
  api<MoneyResult>("/wallet/refunds", { method: "POST", body: { amount }, idempotencyKey });

// 화면 거래 행으로 바꾼다 (TxRow 가 쓰는 형태)
const TITLE: Record<WalletTxType, { title: string; kind: "charge" | "refund" | "payment" | "cashback" }> = {
  CHARGE: { title: "충전", kind: "charge" },
  REFUND: { title: "환불", kind: "refund" },
  PAYMENT_DEVMONEY: { title: "결제 · 데브머니", kind: "payment" },
  PAYMENT_CASHBACK: { title: "결제 · 캐시백 사용", kind: "payment" },
  PAYMENT_CANCEL_DEVMONEY: { title: "결제 취소 · 데브머니 환급", kind: "charge" },
  PAYMENT_CANCEL_CASHBACK: { title: "결제 취소 · 캐시백 환급", kind: "cashback" },
  CASHBACK_EARN: { title: "캐시백 적립", kind: "cashback" },
  CASHBACK_CLAWBACK: { title: "캐시백 회수", kind: "cashback" },
};

export const toRow = (t: WalletTx) => ({
  id: String(t.transactionId),
  title: TITLE[t.type]?.title ?? t.type,
  kind: TITLE[t.type]?.kind ?? ("payment" as const),
  amount: t.amount,
  date: `${t.createdAt.slice(5, 7)}.${t.createdAt.slice(8, 10)} ${t.createdAt.slice(11, 16)}`,
});

// 같은 금액이면 같은 멱등키를 돌려준다 (재시도해도 중복 반영되지 않게). 금액이 바뀌면 새 키를 만든다
export function idemKeyFor(store: { current: { amount: number; key: string } | null }, amount: number) {
  if (!store.current || store.current.amount !== amount) store.current = { amount, key: crypto.randomUUID() };
  return store.current.key;
}

// 거래내역 필터 묶음
export type TxGroup = "전체" | "충전" | "환불" | "결제" | "캐시백";
export const TX_GROUPS: TxGroup[] = ["전체", "충전", "환불", "결제", "캐시백"];
export const groupOf = (type: WalletTxType): Exclude<TxGroup, "전체"> =>
  type === "CHARGE" ? "충전" : type === "REFUND" ? "환불" : type.startsWith("PAYMENT_") ? "결제" : "캐시백";
