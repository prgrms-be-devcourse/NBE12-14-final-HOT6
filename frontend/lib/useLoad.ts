"use client";

import { useCallback, useEffect, useState } from "react";

export type LoadState<T> = { data: T | null; error: string | null; loading: boolean; reload: () => void };

// 비동기 조회 결과를 화면 상태로 바꾼다. deps 가 바뀌면 다시 조회한다
export function useLoad<T>(fetcher: () => Promise<T>, deps: (string | number | undefined)[]): LoadState<T> {
  const [state, setState] = useState<{ data: T | null; error: string | null; loading: boolean }>({ data: null, error: null, loading: true });
  const [tick, setTick] = useState(0);
  const key = deps.join("|");
  useEffect(() => {
    let alive = true;
    fetcher().then(
      (data) => alive && setState({ data, error: null, loading: false }),
      (e: unknown) => alive && setState({ data: null, error: e instanceof Error ? e.message : "불러오지 못했습니다", loading: false }),
    );
    return () => { alive = false; };
    // fetcher 는 deps 로 만든 값만 쓰므로 key 로만 다시 조회한다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, tick]);
  const reload = useCallback(() => { setState((s) => ({ ...s, loading: true })); setTick((t) => t + 1); }, []);
  return { ...state, reload };
}
