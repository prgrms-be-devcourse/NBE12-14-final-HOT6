import { audienceOfPath, clearSession, getSession, LOGIN_API, LOGIN_PATH } from "@/lib/auth";

// 백엔드 공통 응답: { success, code, message, data }
type ApiEnvelope<T> = { success: boolean; code: string | null; message: string | null; data: T | null };

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public code?: string | null,
  ) {
    super(message);
  }
}

// stay401: 401 이 나도 로그인 화면으로 보내지 않고(세션만 지운다) 화면이 "로그인이 필요해요" 안내를 직접 보여 줄 때 쓴다
type Options = { method?: string; body?: unknown; idempotencyKey?: string; stay401?: boolean };

export async function api<T>(path: string, { method = "GET", body, idempotencyKey, stay401 = false }: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;
  const session = getSession();
  if (session) headers["Authorization"] = `Bearer ${session.accessToken}`;

  let res: Response;
  try {
    res = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError("백엔드에 연결할 수 없습니다. 서버가 켜져 있는지 확인해 주세요.", 0);
  }

  let json: ApiEnvelope<T> | null = null;
  try {
    json = (await res.json()) as ApiEnvelope<T>;
  } catch {
    /* JSON 이 아닌 응답 (401 빈 본문 등) */
  }

  // 토큰이 없거나 만료·무효면 세션을 지우고 해당 앱의 로그인 화면으로 보낸다. 로그인 요청 자체의 401(자격 증명 오류)은 화면이 메시지로 보여 준다
  if (res.status === 401 && !Object.values(LOGIN_API).includes(path)) {
    clearSession();
    if (!stay401 && typeof window !== "undefined") window.location.assign(LOGIN_PATH[audienceOfPath(window.location.pathname)]);
  }

  if (!res.ok || !json?.success) {
    const fallback =
      res.status === 401 || res.status === 403
        ? `접근이 거부되었습니다 (${res.status}) — 이 계정의 역할로는 사용할 수 없는 기능일 수 있습니다`
        : `요청에 실패했습니다 (${res.status})`;
    throw new ApiError(json?.message ?? fallback, res.status, json?.code);
  }
  return json.data as T;
}
