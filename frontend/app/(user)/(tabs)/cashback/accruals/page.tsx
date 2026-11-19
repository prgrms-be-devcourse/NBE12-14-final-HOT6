"use client";

import { useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { accruals, wallet } from "@/lib/mock";

const FILTERS = ["전체", "예정", "지급완료", "제외"] as const;
const BADGE = { 예정: "badge-warn", 지급완료: "badge-ok", 제외: "badge-bad" } as const;

export default function AccrualsPage() {
  const [f, setF] = useState<(typeof FILTERS)[number]>("전체");
  const list = accruals.filter((a) => f === "전체" || a.status === f);
  return (
    <>
      <Header title="캐시백 적립내역" back="/cashback" right="2026년 11월" />
      <div className="px-5 pb-4">
        <div className="grid grid-cols-3 gap-2 text-center">
          {[["적립 예정", won(wallet.pendingCashback)], ["지급 완료", won(wallet.paidCashback)], ["제외", "0원"]].map(([k, v]) => (
            <div key={k} className="card-gray !p-3">
              <div className="note">{k}</div>
              <div className="mt-0.5 font-semibold">{v}</div>
            </div>
          ))}
        </div>
        <div className="my-4 flex gap-2" role="tablist">
          {FILTERS.map((x) => (
            <button key={x} type="button" role="tab" aria-selected={f === x} onClick={() => setF(x)} className={`chip ${f === x ? "chip-on" : ""}`}>
              {x}
            </button>
          ))}
        </div>
        <ul>
          {list.map((a) => (
            <li key={a.id} className="flex items-center justify-between border-t border-zinc-200 px-1.5 py-2.5">
              <div>
                <div className="font-medium">{a.merchant}</div>
                <div className="note">{a.date} {a.desc}</div>
              </div>
              <div className="text-right">
                <div className={`font-semibold ${a.status === "제외" ? "text-slate-400" : "amt-cb"}`}>+{a.amount}원</div>
                <span className={`badge ${BADGE[a.status]}`}>{a.status}</span>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
