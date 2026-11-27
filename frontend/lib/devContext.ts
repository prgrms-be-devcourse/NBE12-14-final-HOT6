// 화면 사이에서 넘기는 값(QR 토큰, 가맹점 등록 때 입력한 업종)을 이 브라우저 localStorage 에 잠시 저장한다.
// 회원·지갑·가맹점 식별은 서버가 토큰으로 하므로 여기에 두지 않는다.
export type DevContext = {
  qrToken?: string; // 이용자가 마지막으로 조회한 QR
  merchantQrToken?: string; // 점주가 마지막으로 만든 QR. 이용자 결제 화면에 미리 입력되지 않도록 따로 둔다
  merchantName?: string; merchantAddress?: string; category?: string; eligible?: boolean;
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

// 이전 사용자의 QR 가 다음 로그인에 남지 않도록 QR 토큰(이용자·점주 둘 다)만 지운다.
export function clearQrTokens() {
  try {
    const ctx = loadCtx();
    delete ctx.qrToken;
    delete ctx.merchantQrToken;
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
