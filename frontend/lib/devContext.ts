// 화면 사이에서 넘기는 값(QR 토큰, 가맹점 등록 때 입력한 업종·위치)을 이 브라우저 localStorage 에 잠시 저장한다.
// 회원·지갑·가맹점 식별은 서버가 토큰으로 하므로 여기에 두지 않는다.
export type DevContext = {
  qrToken?: string;
  phone?: string;
  merchantName?: string; merchantAddress?: string; category?: string; eligible?: boolean; lat?: number; lng?: number;
};

const KEY = "devpay.devContext";
export const DEFAULT_CTX: DevContext = {};

export function loadCtx(): DevContext {
  try {
    return { ...DEFAULT_CTX, ...JSON.parse(localStorage.getItem(KEY) ?? "{}") };
  } catch {
    return DEFAULT_CTX;
  }
}

// 로그아웃할 때 이전 사용자가 입력한 QR 가 다음 로그인에 남지 않도록 QR 토큰만 지운다.
export function clearQrToken() {
  try {
    const ctx = loadCtx();
    delete ctx.qrToken;
    localStorage.setItem(KEY, JSON.stringify(ctx));
  } catch {
    /* 저장 불가 환경 */
  }
}

export function saveCtx(patch: Partial<DevContext>) {
  try {
    localStorage.setItem(KEY, JSON.stringify({ ...loadCtx(), ...patch }));
  } catch {
    /* 저장 불가 환경 */
  }
}
