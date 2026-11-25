"use client";

import { useEffect, useState, type ReactNode } from "react";
import Header from "@/components/Header";
import { getMyPayments } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";
import type { UserPaymentItem } from "@/lib/types";

// 탭 이름 → 서버 status (전체는 생략)
const FILTERS = [
  { label: "전체", status: undefined },
  { label: "완료", status: "COMPLETED" },
  { label: "취소", status: "CANCELED" },
] as const;

const PAGE_SIZE = 10;
const fmt = (iso?: string | null) => (iso ? `${iso.slice(5, 7)}.${iso.slice(8, 10)} ${iso.slice(11, 16)}` : "-");
const fmtFull = (iso?: string | null) => (iso ? iso.slice(0, 16).replace("T", " ") : "-");
const payNo = (id: number) => `PAY-${String(id).padStart(4, "0")}`;

// 내 결제 내역. 결제 완료·취소만 최신순으로 보이고, 지갑은 서버가 토큰의 회원으로 찾는다
export default function PayHistoryPage() {
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState<UserPaymentItem | null>(null);
  const f = FILTERS[tab];
  const load = useLoad(() => getMyPayments(f.status, page, PAGE_SIZE), [tab, page]);
  const data = load.data?.payments;

  const pick = (i: number) => {
    setTab(i);
    setPage(0);
    setSel(null);
  };

  // Esc 로 영수증을 닫는다
  useEffect(() => {
    if (!sel) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setSel(null);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [sel]);

  return (
    <>
      <Header title="결제 내역" back="/my" />
      <div className="px-5 pb-4">
        <div className="mb-4 flex gap-2" role="tablist">
          {FILTERS.map((x, i) => (
            <button key={x.label} type="button" role="tab" aria-selected={tab === i} onClick={() => pick(i)} className={`chip ${tab === i ? "chip-on" : ""}`}>
              {x.label}
            </button>
          ))}
        </div>

        {load.loading && !data && <p role="status" className="note py-6 text-center">결제 내역을 불러오는 중이에요…</p>}
        {load.error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">
            {load.error} <button type="button" onClick={load.reload} className="underline">다시 불러오기</button>
          </p>
        )}
        {data && data.content.length === 0 && <p className="note py-6 text-center">결제 내역이 없어요</p>}

        <ul className="space-y-2.5">
          {data?.content.map((p) => {
            const done = p.status === "COMPLETED";
            const label = done ? "완료" : "취소";
            return (
              <li key={p.paymentId}>
                <button type="button" onClick={() => setSel(p)} className="card !p-3.5 w-full text-left">
                  <div className="flex items-center justify-between">
                    <span className="font-medium">{p.merchantName ?? "알 수 없는 가맹점"}</span>
                    <span className={`font-semibold ${done ? "amt-out" : "text-slate-400 line-through"}`}>{won(p.amount)}</span>
                  </div>
                  <div className="mt-0.5 flex items-center justify-between">
                    <span className="note">{fmt(p.completedAt)}</span>
                    <span className={`badge ${done ? "badge-ok" : "badge-bad"}`}>{label}</span>
                  </div>
                </button>
              </li>
            );
          })}
        </ul>

        {data && data.totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between">
            <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">이전</button>
            <span className="note">{page + 1} / {data.totalPages}</span>
            <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">다음</button>
          </div>
        )}
      </div>
      {sel && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-5" onClick={() => setSel(null)}>
          <div role="dialog" aria-modal="true" aria-label="결제 영수증" onClick={(e) => e.stopPropagation()} className="w-full max-w-[390px] rounded-2xl bg-white px-5 pb-5 pt-5">
            <div className="flex items-center justify-between gap-2">
              <span className="font-bold">결제 영수증</span>
              <span className="rounded-md bg-slate-100 px-2.5 py-1 text-[13px] font-bold tabular-nums text-slate-800">결제번호 {payNo(sel.paymentId)}</span>
            </div>
            <div className="mt-4 text-center">
              <div className="font-medium">{sel.merchantName ?? "알 수 없는 가맹점"}</div>
              <div className={`mt-1 text-[32px] font-extrabold tabular-nums tracking-tight ${sel.status === "COMPLETED" ? "amt-out" : "text-slate-400 line-through"}`}>{won(sel.amount)}</div>
              <span className={`badge mt-1 ${sel.status === "COMPLETED" ? "badge-ok" : "badge-bad"}`}>{sel.status === "COMPLETED" ? "결제 완료" : "결제 취소"}</span>
            </div>
            <dl className="mt-4 text-sm">
              {([
                ["데브머니 사용", won(sel.walletUsedAmount)],
                ["캐시백 사용", won(sel.cashbackUsedAmount)],
                ["적립 예정 캐시백", sel.cashbackEarnedAmount ? sel.status === "CANCELED" ? (<><span className="line-through">{won(sel.cashbackEarnedAmount)}</span> 적립 예정 취소</>) : `+${won(sel.cashbackEarnedAmount)}` : "없음"],
                ["결제 시각", fmtFull(sel.completedAt)],
                ...(sel.status === "CANCELED" ? ([["취소 시각", fmtFull(sel.canceledAt)]] as [string, string][]) : []),
              ] as [string, ReactNode][]).map(([k, v]) => (
                <div key={k} className="flex justify-between border-t border-slate-100 py-2.5">
                  <dt className={k === "취소 시각" ? "font-semibold text-red-600" : "text-slate-500"}>{k}</dt>
                  <dd className={`font-medium tabular-nums ${k === "취소 시각" ? "font-bold text-red-600" : k === "적립 예정 캐시백" && sel.cashbackEarnedAmount ? (sel.status === "CANCELED" ? "text-red-600" : "text-emerald-700") : ""}`}>{v}</dd>
                </div>
              ))}
            </dl>
            <button type="button" onClick={() => setSel(null)} className="btn btn-primary mt-3">닫기</button>
          </div>
        </div>
      )}
    </>
  );
}
