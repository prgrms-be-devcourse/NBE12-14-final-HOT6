"use client";

import { useEffect, useState } from "react";
import Header from "@/components/Header";
import MerchantTabs from "@/components/MerchantTabs";
import { ApiError } from "@/lib/api";
import { addDays, ymd } from "@/lib/dates";
import { getDailySettlements } from "@/lib/endpoints";
import { won } from "@/lib/format";
import type { DailySettlementResponse } from "@/lib/types";

type Load = { kind: "loading" } | { kind: "error"; message: string } | { kind: "ok"; rows: DailySettlementResponse[] };

const RECENT = 7;

// 하루치 정산의 계산 내역. 가장 최근 정산 카드와 "최근 정산" 목록의 펼침에서 같이 쓴다
function SettlementBreakdown({ d }: { d: DailySettlementResponse }) {
  return (
    <dl className="space-y-1.5 text-sm">
      <div className="flex justify-between"><dt>결제</dt><dd>{won(d.paymentTotal)}</dd></div>
      <div className="flex justify-between"><dt>− 취소</dt><dd className="amt-out">{won(d.cancelTotal)}</dd></div>
      <div className="flex justify-between border-t border-slate-200 pt-1.5 font-semibold"><dt>= 순매출</dt><dd className="amt-in">{won(d.netSales)}</dd></div>
      <div className="flex justify-between text-zinc-500"><dt>전일 이월</dt><dd>{won(d.carryIn)}</dd></div>
      <div className="flex justify-between font-semibold"><dt>확정 정산액</dt><dd>{won(d.confirmedAmount)}</dd></div>
      <div className="flex justify-between text-zinc-500"><dt>다음 날 이월</dt><dd>{won(d.carryOut)}</dd></div>
    </dl>
  );
}

export default function MerchantDailyPage() {
  const [load, setLoad] = useState<Load>({ kind: "loading" });
  const [openDate, setOpenDate] = useState<string | null>(null); // 상세를 펼친 정산 날짜 (한 번에 하나)

  useEffect(() => {
    let alive = true;
    const today = new Date();
    getDailySettlements(ymd(addDays(today, -30)), ymd(today))
      .then((rows) => {
        // 서버는 오래된 날짜부터 주므로, 가장 최근 정산이 위로 오게 뒤집는다
        if (alive) setLoad({ kind: "ok", rows: [...rows].reverse() });
      })
      .catch((e) => {
        if (alive) setLoad({ kind: "error", message: e instanceof ApiError ? e.message : "일 정산을 불러오지 못했어요" });
      });
    return () => {
      alive = false;
    };
  }, []);

  const rows = load.kind === "ok" ? load.rows : [];
  const latest = rows[0];

  return (
    <>
      <Header title="매출 · 정산" />
      <MerchantTabs />
      <div className="space-y-3 px-5 pb-5">
        {load.kind === "loading" && <p role="status" className="note text-center">일 정산을 불러오는 중이에요…</p>}
        {load.kind === "error" && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{load.message}</p>}
        {load.kind === "ok" && !latest && (
          <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
            아직 정산 내역이 없어요. 일 정산은 매일 00:10에 전날 매출로 만들어져요.
          </p>
        )}
        {latest && (
          <section className="card">
            <div className="flex items-center justify-between"><span className="font-medium">{latest.settlementDate} 일 정산</span><span className="badge badge-ok">생성됨</span></div>
            <div className="mt-3"><SettlementBreakdown d={latest} /></div>
            <p className="note mt-2">취소로 순매출이 마이너스가 되면 확정액은 0원이고, 부족분은 다음 날로 이월돼요.</p>
          </section>
        )}
        {rows.length > 1 && (
          <>
            <h2 className="px-1.5 pt-2 text-base font-medium">최근 정산</h2>
            <ul>
              {rows.slice(1, RECENT + 1).map((d) => {
                const open = openDate === d.settlementDate;
                return (
                  <li key={d.settlementDate} className="border-t border-zinc-200">
                    <button type="button" onClick={() => setOpenDate(open ? null : d.settlementDate)} aria-expanded={open} className="flex w-full items-center justify-between px-1.5 py-2.5 text-left">
                      <span>{d.settlementDate}</span>
                      <span className="flex items-center gap-2">
                        <span className="font-medium">{won(d.confirmedAmount)}</span>
                        <span aria-hidden className="text-slate-400">{open ? "▴" : "▾"}</span>
                      </span>
                    </button>
                    {open && <div className="px-1.5 pb-3"><SettlementBreakdown d={d} /></div>}
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </div>
    </>
  );
}
