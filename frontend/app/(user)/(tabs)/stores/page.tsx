"use client";

import { useState } from "react";
import Header from "@/components/Header";
import { ApiError } from "@/lib/api";
import { searchMerchants } from "@/lib/endpoints";
import type { MerchantResponse } from "@/lib/types";

const PAGE_SIZE = 20;

type State =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; query: string; items: MerchantResponse[]; page: number; totalPages: number; total: number };

export default function StoresPage() {
  const [q, setQ] = useState("");
  const [state, setState] = useState<State>({ kind: "idle" });
  const [more, setMore] = useState(false);

  // 이름 부분 일치 검색. page 가 0 이면 새로 검색하고, 1 이상이면 기존 결과 뒤에 이어 붙인다
  const search = async (query: string, page: number) => {
    if (page === 0) setState({ kind: "loading" });
    else setMore(true);
    try {
      const res = await searchMerchants(query, page, PAGE_SIZE);
      setState((prev) => ({
        kind: "ok",
        query,
        items: page > 0 && prev.kind === "ok" ? [...prev.items, ...res.content] : res.content,
        page: res.page,
        totalPages: res.totalPages,
        total: res.totalElements,
      }));
    } catch (e) {
      setState({ kind: "error", message: e instanceof ApiError ? e.message : "가맹점을 찾지 못했어요" });
    } finally {
      setMore(false);
    }
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const query = q.trim();
    if (query) search(query, 0);
  };

  return (
    <>
      <Header title="가맹점 찾기" />
      <div className="space-y-3 px-5 pb-4">
        <form onSubmit={submit} className="flex gap-2">
          <label className="block min-w-0 flex-1">
            <span className="sr-only">매장명으로 찾기</span>
            <input type="search" className="field" placeholder="매장명으로 찾기" value={q} onChange={(e) => setQ(e.target.value)} />
          </label>
          <button type="submit" disabled={!q.trim() || state.kind === "loading"} className="btn btn-outline btn-sm !w-auto shrink-0 disabled:opacity-40">검색</button>
        </form>

        {state.kind === "idle" && <p className="note text-center">가맹점 이름을 입력해 찾아보세요. 이름의 일부만 입력해도 돼요.</p>}
        {state.kind === "loading" && <p role="status" className="note text-center">가맹점을 찾는 중이에요…</p>}
        {state.kind === "error" && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{state.message}</p>}

        {state.kind === "ok" && (
          <>
            <h2 className="px-1 pt-1 text-sm font-semibold">&quot;{state.query}&quot; 검색 결과 {state.total}곳</h2>
            {state.items.length === 0 ? (
              <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">찾으시는 가맹점이 없어요. 다른 이름으로 찾아보세요.</p>
            ) : (
              <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
                {state.items.map((s) => (
                  <li key={s.merchantId} className="flex items-center gap-3 px-3.5 py-3">
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{s.name}</span>
                      <span className="block truncate text-xs text-slate-500">{s.address || "주소 정보 없음"}</span>
                    </span>
                    <span className={`badge shrink-0 ${s.cashbackEligible ? "badge-ok" : "badge-warn"}`}>{s.cashbackEligible ? "적립 가능" : "적립 제외"}</span>
                  </li>
                ))}
              </ul>
            )}
            {state.page + 1 < state.totalPages && (
              <button type="button" disabled={more} onClick={() => search(state.query, state.page + 1)} className="btn btn-outline disabled:opacity-40">{more ? "불러오는 중…" : "더 보기"}</button>
            )}
            <p className="note text-center">지도와 업종으로 찾는 기능은 준비 중이에요.</p>
          </>
        )}
      </div>
    </>
  );
}
