"use client";

import { useEffect, useState } from "react";
import { getSession } from "@/lib/auth";

const RETRY_MS = 3_000;
// 서버는 20초마다 상태를 보낸다. 이 시간 동안 아무것도 오지 않으면 끊긴 연결로 보고 다시 연결한다
const SILENCE_MS = 45_000;

// 이번 달 캐시백 적립이 중단됐는지를 서버에서 실시간으로 받는다(SSE). 아직 받기 전이면 null.
// 기본 EventSource 는 Authorization 헤더를 실을 수 없어 fetch 로 응답을 이어 읽는다.
// 화면을 벗어나면(숨김 포함) 연결을 닫고, 끊기면 다시 연결한다.
export function useCashbackPaused(enabled = true): boolean | null {
  const [paused, setPaused] = useState<boolean | null>(null);

  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    let controller: AbortController | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;

    const connect = async () => {
      const session = getSession();
      if (!session) return;
      const current = new AbortController();
      controller = current;
      let silence = setTimeout(() => current.abort(), SILENCE_MS);
      try {
        const res = await fetch("/api/cashback/status/stream", {
          headers: { Accept: "text/event-stream", Authorization: `Bearer ${session.accessToken}` },
          cache: "no-store",
          signal: current.signal,
        });
        // 로그인이 만료됐거나 권한이 없으면 다시 시도해도 같다. 로그인 이동은 다른 조회가 맡는다
        if (res.status === 401 || res.status === 403) return;
        if (res.ok && res.body) {
          const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
          let buffer = "";
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            clearTimeout(silence);
            silence = setTimeout(() => current.abort(), SILENCE_MS);
            const lines = (buffer + value).split("\n");
            buffer = lines.pop() ?? "";
            for (const line of lines) {
              if (!line.startsWith("data:")) continue;
              const data = JSON.parse(line.slice(5)) as { paused: boolean };
              if (alive) setPaused(data.paused);
            }
          }
        }
      } catch {
        // 연결 실패·끊김: 아래에서 다시 연결한다
      } finally {
        clearTimeout(silence);
      }
      if (alive) retry = setTimeout(connect, RETRY_MS);
    };

    void connect();
    return () => {
      alive = false;
      clearTimeout(retry);
      controller?.abort();
    };
  }, [enabled]);

  return paused;
}
