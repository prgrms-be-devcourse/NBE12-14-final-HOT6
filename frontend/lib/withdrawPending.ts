// 탈퇴 실행에 쓸 확인한 환불 예정액과 멱등키를 보관한다 → 간편비밀번호 확인 화면을 거쳐도, 새로고침해도 같은 키로 보낸다
export type PendingWithdrawal = { expected: number; key: string };
const KEY = "devpay.pendingWithdrawal";

export const newWithdrawal = (expected: number): PendingWithdrawal => ({ expected, key: crypto.randomUUID() });

export function saveWithdrawal(p: PendingWithdrawal) {
  try { sessionStorage.setItem(KEY, JSON.stringify(p)); } catch { /* ignore */ }
}
export function loadWithdrawal(): PendingWithdrawal | null {
  try { return JSON.parse(sessionStorage.getItem(KEY) ?? "null"); } catch { return null; }
}
export function clearWithdrawal() {
  try { sessionStorage.removeItem(KEY); } catch { /* ignore */ }
}
