import { api } from "@/lib/api";
import type { PageResponse } from "@/lib/types";

// 지갑 거래 내역·충전·환불과 모의계좌 잔액. 지갑은 서버가 토큰의 회원으로 찾는다 (내 지갑 조회는 endpoints.getMyWallet)
export type WalletTxType =
  | "CHARGE" | "REFUND" | "PAYMENT_DEVMONEY" | "PAYMENT_CASHBACK"
  | "PAYMENT_CANCEL_DEVMONEY" | "PAYMENT_CANCEL_CASHBACK" | "CASHBACK_EARN" | "CASHBACK_CLAWBACK";
// 금액은 입금이 +, 차감이 -. balanceAfter 는 그 거래 직후의 잔액(캐시백 거래는 캐시백 잔액)
export type WalletTx = { transactionId: number; type: WalletTxType; amount: number; balanceAfter: number; createdAt: string };
export type MoneyResult = { amount: number; createdAt: string };

export const getWalletTransactions = (page = 0, size = 20) =>
  api<PageResponse<WalletTx>>(`/wallet/transactions?page=${page}&size=${size}`);

// 같은 멱등키로 다시 보내면 한 번만 반영하고 처음 결과를 돌려준다. 금액이 다르면 거절(COM-003)되므로 금액이 바뀌면 새 키를 쓴다
export const chargeWallet = (amount: number, idempotencyKey: string) =>
  api<MoneyResult>("/wallet/charges", { method: "POST", body: { amount }, idempotencyKey });
export const refundWallet = (amount: number, idempotencyKey: string) =>
  api<MoneyResult>("/wallet/refunds", { method: "POST", body: { amount }, idempotencyKey });

// 내 모의계좌(가입하면 500만원). 충전하면 줄고 환불하면 는다. 가맹점 정산 계좌(endpoints.getMyAccount)와는 다르다
export const getAccountBalance = () => api<{ balance: number }>("/wallet/account");

// 같은 금액이면 같은 멱등키를 돌려준다 (재시도해도 중복 반영되지 않게). 금액이 바뀌면 새 키를 만든다
export function idemKeyFor(store: { current: { amount: number; key: string } | null }, amount: number) {
  if (!store.current || store.current.amount !== amount) store.current = { amount, key: crypto.randomUUID() };
  return store.current.key;
}
