// 인증 API 가 머지되기 전까지, 로그인한 사용자 대신 쓰는 개발용 식별자 (브라우저 localStorage)
export type DevContext = {
  ownerId: number; walletId: number; merchantId: number; qrId: number;
  merchantName?: string; merchantAddress?: string; category?: string; eligible?: boolean; lat?: number; lng?: number;
};

const KEY = "devpay.devContext";
export const DEFAULT_CTX: DevContext = { ownerId: 1, walletId: 1, merchantId: 1, qrId: 1 };

export function loadCtx(): DevContext {
  try {
    return { ...DEFAULT_CTX, ...JSON.parse(localStorage.getItem(KEY) ?? "{}") };
  } catch {
    return DEFAULT_CTX;
  }
}

export function saveCtx(patch: Partial<DevContext>) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ ...loadCtx(), ...patch }));
  } catch {
    /* 저장 불가 환경 */
  }
}
