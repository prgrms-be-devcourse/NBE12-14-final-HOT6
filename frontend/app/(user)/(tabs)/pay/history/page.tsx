"use client";

import { useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { payments } from "@/lib/mock";

const FILTERS = ["전체", "완료", "취소"] as const;

export default function PayHistoryPage() {
  const [f, setF] = useState<(typeof FILTERS)[number]>("전체");
  const list = payments.filter((p) => f === "전체" || p.status === f);

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
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
