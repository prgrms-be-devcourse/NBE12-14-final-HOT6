"use client";

import { useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { payments } from "@/lib/mock";

const FILTERS = ["전체", "완료", "취소"] as const;

export default function PayHistoryPage() {
  const [f, setF] = useState<(typeof FILTERS)[number]>("전체");
  const [canceled, setCanceled] = useState<string[]>([]);
  const rows = payments.map((p) => (canceled.includes(p.id) ? { ...p, status: "취소" as const } : p));
  const list = rows.filter((p) => f === "전체" || p.status === f);

  return (
    <>
      <Header title="결제 내역" back="/my" />
      <div className="px-5 pb-4">
        <div className="mb-4 flex gap-2" role="tablist">
          {FILTERS.map((x) => (
            <button key={x} type="button" role="tab" aria-selected={f === x} onClick={() => setF(x)} className={`chip ${f === x ? "chip-on" : ""}`}>
              {x}
            </button>
          ))}
        </div>
        <ul className="space-y-2.5">
          {list.map((p) => (
            <li key={p.id} className="card !p-3.5">
              <div className="flex items-center justify-between">
                <span className="font-medium">{p.merchant}</span>
                <span className={`font-semibold ${p.status === "완료" ? "amt-out" : "text-slate-400 line-through"}`}>{won(p.amount)}</span>
              </div>
              <div className="mt-0.5 flex items-center justify-between">
                <span className="note">{p.date} · 결제{p.status} · 결제번호 {p.no}</span>
                <span className={`badge ${p.status === "완료" ? "badge-ok" : "badge-bad"}`}>{p.status}</span>
              </div>
              {p.status === "완료" && (
                <button type="button" onClick={() => setCanceled((c) => [...c, p.id])} className="btn btn-danger btn-sm mt-2.5">
                  결제 취소
                </button>
              )}
            </li>
          ))}
        </ul>
        <div className="card-dash mt-4">
          <ul className="space-y-0.5">
            <li>• 취소 시 지갑 환급 · 적립 취소</li>
            <li>• 중복 취소 방지</li>
            <li>• 같은 요청 재전송 시 기존 결과 반환</li>
          </ul>
        </div>
      </div>
    </>
  );
}
