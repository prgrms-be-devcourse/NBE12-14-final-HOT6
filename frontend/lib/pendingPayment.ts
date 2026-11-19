import type { PaymentRequest } from "@/lib/types";

// 결제 요청과 멱등키를 함께 보관 → 새로고침/재시도 해도 같은 키로 보내 결과를 재조회한다
export type PendingPayment = { request: PaymentRequest; key: string };
const KEY = "devpay.pendingPayment";

export const newPending = (request: PaymentRequest): PendingPayment => ({ request, key: crypto.randomUUID() });

export function savePending(p: PendingPayment) {
  try { sessionStorage.setItem(KEY, JSON.stringify(p)); } catch { /* ignore */ }
}
export function loadPending(): PendingPayment | null {
  try { return JSON.parse(sessionStorage.getItem(KEY) ?? "null"); } catch { return null; }
}
export function clearPending() {
  try { sessionStorage.removeItem(KEY); } catch { /* ignore */ }
}
