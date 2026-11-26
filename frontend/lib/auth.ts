// 로그인 세션. 액세스 토큰과 만료 시각, 역할을 브라우저 localStorage 에 둔다.
// 역할은 화면 이동에만 쓴다. 접근 통제는 서버가 토큰으로 판단한다.
import { clearQrTokens } from "@/lib/devContext";

export type Role = "USER" | "OWNER" | "ADMIN";
export type Audience = "user" | "merchant" | "admin";

export type Session = { accessToken: string; expiresAt: number; role: Role; userId: number; pinVerified?: boolean };

// 백엔드 LoginResponse
type LoginResponse = { accessToken: string; tokenType: string; expiresIn: number };

const KEY = "devpay.session";

// 앱(화면 묶음)마다 들어올 수 있는 역할. 시민 기능은 가맹점주도 쓴다.
const ALLOWED: Record<Audience, Role[]> = { user: ["USER", "OWNER"], merchant: ["OWNER"], admin: ["ADMIN"] };

export const LOGIN_PATH: Record<Audience, string> = { user: "/login", merchant: "/merchant/login", admin: "/admin/login" };

// 화면마다 부르는 로그인 API. 점주 계정은 점주 로그인만 받는다
export const LOGIN_API: Record<Audience, string> = { user: "/auth/login", merchant: "/auth/owner/login", admin: "/auth/login" };

export const canEnter = (role: Role, audience: Audience) => ALLOWED[audience].includes(role);

// 로그인 직후 처음 보여 줄 화면
export const HOME_PATH: Record<Audience, string> = { user: "/pin?next=/wallet", merchant: "/merchant/pin", admin: "/admin" };

export const audienceOfPath = (path: string): Audience =>
  path.startsWith("/admin") ? "admin" : path.startsWith("/merchant") ? "merchant" : "user";

// JWT 본문(payload)에서 역할과 회원 ID 를 꺼낸다. 서명 검증은 서버가 한다.
function decode(token: string): { role: Role; userId: number } | null {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const json = JSON.parse(atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, "=")));
    if (!["USER", "OWNER", "ADMIN"].includes(json.role)) return null;
    return { role: json.role as Role, userId: Number(json.sub) };
  } catch {
    return null;
  }
}

export function saveLogin(res: LoginResponse): Session | null {
  const claims = decode(res.accessToken);
  if (!claims) return null;
  clearQrTokens(); // 로그아웃 없이 다른 계정으로 바로 로그인해도 이전 사용자의 QR 이 남지 않게 한다
  const session: Session = { accessToken: res.accessToken, expiresAt: Date.now() + res.expiresIn * 1000, ...claims };
  try {
    localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    /* 저장 불가 환경 */
  }
  return session;
}

// 점주 화면은 간편비밀번호 확인을 통과해야 연다. 확인 결과는 이번 로그인에만 남고, 다시 로그인하면 다시 확인한다
export function markPinVerified() {
  const session = getSession();
  if (!session) return;
  try {
    localStorage.setItem(KEY, JSON.stringify({ ...session, pinVerified: true }));
  } catch {
    /* 저장 불가 환경 */
  }
}

// 저장된 세션 문자열 그대로(없으면 null). 화면이 저장소 변화를 구독할 때 쓴다.
export function readRawSession(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

// 저장 문자열을 세션으로 해석한다. 만료는 보지 않는다.
export function parseSession(raw: string | null): Session | null {
  try {
    const session = raw ? (JSON.parse(raw) as Session) : null;
    return session?.accessToken ? session : null;
  } catch {
    return null;
  }
}

// 만료됐거나 없으면 null. 만료된 세션은 지운다.
export function getSession(): Session | null {
  const session = parseSession(readRawSession());
  if (session && session.expiresAt <= Date.now()) {
    clearSession();
    return null;
  }
  return session;
}

export function clearSession() {
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* 저장 불가 환경 */
  }
}

// 사용자가 로그아웃을 눌렀을 때. 세션과 함께 이전에 입력한 QR 도 지운다(만료로 세션이 지워질 때는 QR 을 그대로 둔다).
export function logout() {
  clearSession();
  clearQrTokens();
}
