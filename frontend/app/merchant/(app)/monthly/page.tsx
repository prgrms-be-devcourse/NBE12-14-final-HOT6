"use client";

import { useEffect, useState } from "react";
import Header from "@/components/Header";
import MerchantTabs from "@/components/MerchantTabs";
import { ApiError } from "@/lib/api";
import { monthsAgo } from "@/lib/dates";
import { getMonthlySettlements, getMyAccount } from "@/lib/endpoints";
import { won } from "@/lib/format";
import type { MerchantAccountResponse, MonthlySettlementResponse } from "@/lib/types";

type Load<T> = { kind: "loading" } | { kind: "error"; message: string } | { kind: "ok"; data: T };

const fmtTime = (iso: string) => iso.slice(0, 16).replace("T", " ");
const monthLabel = (ym: string) => `${ym.slice(0, 4)}년 ${Number(ym.slice(5))}월`;

export default function MerchantMonthlyPage() {
  const [settlements, setSettlements] = useState<Load<MonthlySettlementResponse[]>>({ kind: "loading" });
  const [account, setAccount] = useState<Load<MerchantAccountResponse>>({ kind: "loading" });

  useEffect(() => {
    let alive = true;
    const now = new Date();
    getMonthlySettlements(monthsAgo(now, 11), monthsAgo(now, 0))
      .then((rows) => {
        if (alive) setSettlements({ kind: "ok", data: [...rows].reverse() });
      })
      .catch((e) => {
        if (alive) setSettlements({ kind: "error", message: e instanceof ApiError ? e.message : "월 정산을 불러오지 못했어요" });
      });
    getMyAccount()
      .then((data) => {
        if (alive) setAccount({ kind: "ok", data });
      })
      .catch((e) => {
        if (alive) setAccount({ kind: "error", message: e instanceof ApiError ? e.message : "계좌 잔액을 불러오지 못했어요" });
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <>
      <Header title="매출 · 정산" />
      <MerchantTabs />
      <div className="space-y-3 px-5 pb-5">
        <section className="card">
          <div className="note">내 계좌 잔액</div>
          {account.kind === "ok" && <div className="amt-in text-2xl font-bold">{won(account.data.balance)}</div>}
          {account.kind === "loading" && <p role="status" className="note mt-1">불러오는 중이에요…</p>}
          {account.kind === "error" && <p role="alert" className="mt-1 text-[13px] font-medium text-red-700">{account.message}</p>}
          <p className="note mt-2">월 정산 지급액이 내 계좌로 바로 들어와요.</p>
        </section>

        <h2 className="px-1.5 pt-2 text-base font-medium">월 정산 내역</h2>
        {settlements.kind === "loading" && <p role="status" className="note text-center">월 정산을 불러오는 중이에요…</p>}
        {settlements.kind === "error" && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{settlements.message}</p>}
        {settlements.kind === "ok" && settlements.data.length === 0 && (
          <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
            아직 지급받은 월 정산이 없어요. 월 정산은 매달 1일 00:30에 전월 확정액으로 만들어져요.
          </p>
        )}
        {settlements.kind === "ok" && settlements.data.length > 0 && (
          <ul>
            {settlements.data.map((s) => (
              <li key={s.settlementMonth} className="flex items-center justify-between border-t border-zinc-200 px-1.5 py-2.5">
                <div>
                  <div className="font-medium">{monthLabel(s.settlementMonth)}</div>
                  <div className="note">{s.paidAt ? `지급 ${fmtTime(s.paidAt)}` : "지급 전"}</div>
                </div>
                <div className="text-right">
                  <div className="amt-in font-semibold">{won(s.payoutAmount)}</div>
                  <span className={`badge ${s.status === "PAID" ? "badge-ok" : "badge-warn"}`}>{s.status === "PAID" ? "지급 완료" : "지급 대기"}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
        <p className="note">지급이 실패한 건은 다음 날 다시 시도해요.</p>
      </div>
    </>
  );
}
