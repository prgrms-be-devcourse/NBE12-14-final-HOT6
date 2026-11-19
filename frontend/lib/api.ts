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

type Options = { method?: string; body?: unknown; idempotencyKey?: string };

export async function api<T>(path: string, { method = "GET", body, idempotencyKey }: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (idempotencyKey) headers["Idempotency-Key"] = idempotencyKey;

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

  if (!res.ok || !json?.success) {
    const fallback =
      res.status === 401 || res.status === 403
        ? `접근이 거부되었습니다 (${res.status}) — 인증 연동 전이라 백엔드 SecurityConfig 에서 이 API 가 아직 열려 있지 않을 수 있습니다`
        : `요청에 실패했습니다 (${res.status})`;
    throw new ApiError(json?.message ?? fallback, res.status, json?.code);
  }
  return json.data as T;
}
