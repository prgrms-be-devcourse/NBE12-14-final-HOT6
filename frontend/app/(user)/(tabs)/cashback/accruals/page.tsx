"use client";

import { useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { EXCLUSION_TEXT, STATUS_BADGE, STATUS_LABEL, getCashbackSummary, getEarnings, mmdd, type EarningStatus } from "@/lib/cashbackApi";
import { useLoad } from "@/lib/useLoad";

const FILTERS: { label: string; status?: EarningStatus }[] = [
  { label: "전체" }, { label: "예정", status: "PENDING" }, { label: "지급완료", status: "PAID" }, { label: "제외", status: "EXCLUDED" },
];

export default function AccrualsPage() {
  const [f, setF] = useState(FILTERS[0]);
  const summary = useLoad(() => getCashbackSummary(), []);
  const list = useLoad(() => getEarnings(f.status, 0, 50), [f.status]);
  const s = summary.data;

  return (
    <>
      <Header title="캐시백 적립내역" back="/cashback" right={s ? `${s.year}년 ${s.month}월` : ""} />
      <div className="px-5 pb-4">
        <div className="grid grid-cols-3 gap-2 text-center">
          {[["적립 예정", s ? won(s.pendingAmount) : "–"], ["지급 완료", s ? won(s.paidAmount) : "–"], ["제외", s ? `${s.excludedCount}건` : "–"]].map(([k, v]) => (
            <div key={k} className="card-gray !p-3">
              <div className="note">{k}</div>
              <div className="mt-0.5 font-semibold">{v}</div>
            </div>
          ))}
        </div>
        <div className="my-4 flex gap-2" role="tablist">
          {FILTERS.map((x) => (
            <button key={x.label} type="button" role="tab" aria-selected={f.label === x.label} onClick={() => setF(x)} className={`chip ${f.label === x.label ? "chip-on" : ""}`}>
              {x.label}
            </button>
          ))}
        </div>
        {list.error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-[13px] text-red-700">{list.error}</p>}
        {list.data && list.data.content.length === 0 && <p className="note">내역이 없어요.</p>}
        <ul>
          {list.data?.content.map((a) => (
            <li key={a.earningId} className="flex items-center justify-between border-t border-zinc-200 px-1.5 py-2.5">
              <div>
                <div className="font-medium">결제 #{a.paymentId}</div>
                <div className="note">{mmdd(a.requestedAt)} {a.status === "EXCLUDED" ? `제외: ${EXCLUSION_TEXT[a.exclusionReason ?? "NONE"]}` : ""}</div>
              </div>
              <div className="text-right">
                <div className={`font-semibold ${a.status === "EXCLUDED" ? "text-slate-400" : "amt-cb"}`}>+{a.amount}원</div>
                <span className={`badge ${STATUS_BADGE[a.status]}`}>{STATUS_LABEL[a.status]}</span>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
