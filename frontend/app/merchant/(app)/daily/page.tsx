"use client";

import { useState } from "react";
import Header from "@/components/Header";
import MerchantTabs from "@/components/MerchantTabs";
import { addDays, ymd } from "@/lib/dates";
import { getDailySettlements } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";
import type { DailySettlementResponse } from "@/lib/types";

// 일 정산은 어제까지 만들어지므로 조회 기간은 어제에서 끝난다
const RANGES = [
  { label: "어제", days: 1 },
  { label: "7일", days: 7 },
  { label: "1개월", days: 30 },
  { label: "3개월", days: 90 },
] as const;
const CUSTOM = -1;
const WINDOW_DAYS = 31; // 서버는 한 번에 31일까지만 조회할 수 있어 기간을 나눠 요청한다
const MAX_CUSTOM_DAYS = 365;
const DAY_MS = 86_400_000;
const PAGE_SIZE = 5;

const toDate = (s: string) => new Date(`${s}T00:00:00+09:00`);
const shift = (s: string, days: number) => ymd(addDays(toDate(s), days));
const daysBetween = (from: string, to: string) => Math.round((toDate(to).getTime() - toDate(from).getTime()) / DAY_MS) + 1;

async function fetchRange(from: string, to: string) {
  const windows: [string, string][] = [];
  const end = toDate(to).getTime();
  for (let cur = toDate(from); cur.getTime() <= end; cur = addDays(cur, WINDOW_DAYS)) {
    const last = addDays(cur, WINDOW_DAYS - 1);
    windows.push([ymd(cur), ymd(last.getTime() < end ? last : toDate(to))]);
  }
  const parts = await Promise.all(windows.map(([f, t]) => getDailySettlements(f, t)));
  // 서버는 오래된 날짜부터 주므로, 가장 최근 정산이 위로 오게 뒤집는다
  return { from, to, rows: parts.flat().reverse() };
}

const NEGATIVE_NOTE = "취소로 순매출이 마이너스가 되면 확정액은 0원이고, 부족분은 다음 날로 이월돼요.";

function SettlementBreakdown({ d, large = false }: { d: DailySettlementResponse; large?: boolean }) {
  return (
    <dl className={large ? "space-y-2.5 text-[15px]" : "space-y-1.5 text-sm"}>
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
  const yesterday = ymd(addDays(new Date(), -1));
  const [preset, setPreset] = useState(0);
  const [customFrom, setCustomFrom] = useState(() => ymd(addDays(new Date(), -30)));
  const [customTo, setCustomTo] = useState(yesterday);
  const [applied, setApplied] = useState(() => ({ from: ymd(addDays(new Date(), -30)), to: yesterday }));
  const [openDate, setOpenDate] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const custom = preset === CUSTOM;
  const onlyYesterday = !custom && RANGES[preset].days === 1;
  const from = custom ? applied.from : ymd(addDays(new Date(), -1 - (RANGES[preset].days - 1)));
  const to = custom ? applied.to : yesterday;
  const load = useLoad(() => fetchRange(from, to), [from, to]);
  const ready = load.data !== null && load.data.from === from && load.data.to === to;
  const rows = ready ? load.data!.rows : [];
  const pageCount = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const current = Math.min(page, pageCount - 1);
  const pageRows = rows.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE);

  const customError = !customFrom || !customTo
    ? "시작일과 종료일을 선택해 주세요"
    : customFrom > customTo
      ? "시작일이 종료일보다 늦어요"
      : customTo > yesterday
        ? "종료일은 어제까지만 선택할 수 있어요"
        : daysBetween(customFrom, customTo) > MAX_CUSTOM_DAYS
          ? "최대 1년까지 조회할 수 있어요"
          : "";
  const fromMin = customTo ? shift(customTo, -(MAX_CUSTOM_DAYS - 1)) : undefined;
  const fromMax = customTo && customTo < yesterday ? customTo : yesterday;
  const toMin = customFrom || undefined;
  const toMax = customFrom && shift(customFrom, MAX_CUSTOM_DAYS - 1) < yesterday ? shift(customFrom, MAX_CUSTOM_DAYS - 1) : yesterday;

  const pick = (i: number) => {
    setPreset(i);
    setOpenDate(null);
    setPage(0);
  };
  const applyCustom = () => {
    if (customError) return;
    setApplied({ from: customFrom, to: customTo });
    setOpenDate(null);
    setPage(0);
  };
  const go = (p: number) => {
    setPage(p);
    setOpenDate(null);
  };

  const confirmedSum = rows.reduce((s, d) => s + d.confirmedAmount, 0);
  const netSum = rows.reduce((s, d) => s + d.netSales, 0);

  return (
    <>
      <Header title="매출 · 정산" />
      <MerchantTabs />
      <div className="space-y-3 px-5 pb-5">
        <div className="flex flex-wrap gap-2" role="group" aria-label="조회 기간">
          {RANGES.map((x, i) => (
            <button key={x.label} type="button" onClick={() => pick(i)} aria-pressed={preset === i} className={`chip ${preset === i ? "chip-on" : ""}`}>
              {x.label}
            </button>
          ))}
          <button type="button" onClick={() => pick(CUSTOM)} aria-pressed={custom} className={`chip ${custom ? "chip-on" : ""}`}>
            직접 설정
          </button>
        </div>

        {custom && (
          <section className="card space-y-3">
            <div className="grid grid-cols-2 gap-2.5">
              <label className="block"><span className="label">시작일</span><input type="date" className="field" value={customFrom} min={fromMin} max={fromMax} onChange={(e) => setCustomFrom(e.target.value)} /></label>
              <label className="block"><span className="label">종료일</span><input type="date" className="field" value={customTo} min={toMin} max={toMax} onChange={(e) => setCustomTo(e.target.value)} /></label>
            </div>
            <p className="note">최대 1년까지 조회할 수 있어요</p>
            {customError && <p role="alert" className="text-[13px] font-medium text-red-600">{customError}</p>}
            <button type="button" onClick={applyCustom} disabled={customError !== ""} className="btn btn-brand disabled:opacity-40">조회</button>
          </section>
        )}

        {load.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{load.error}</p>}
        {!ready && !load.error && <p role="status" className="note text-center">일 정산을 불러오는 중이에요…</p>}

        {ready && onlyYesterday && (
          rows[0] ? (
            <section className="card !p-5">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-600">{rows[0].settlementDate} 정산</span>
                <span className="badge badge-ok">어제 정산</span>
              </div>
              <div className="mt-3 text-4xl font-bold leading-tight tabular-nums text-brand-700">{won(rows[0].confirmedAmount)}</div>
              <p className="note mt-1">확정 정산액</p>
              <div className="mt-4 border-t border-slate-200 pt-4"><SettlementBreakdown d={rows[0]} large /></div>
              <p className="note mt-4">{NEGATIVE_NOTE}</p>
            </section>
          ) : (
            <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
              어제({yesterday}) 정산이 아직 없어요. 일 정산은 매일 00:10에 전날 매출로 만들어져요.
            </p>
          )
        )}

        {ready && !onlyYesterday && (
          <>
            <section className="card">
              <div className="note">{from === to ? from : `${from} ~ ${to}`} 정산</div>
              <div className="mt-1 text-2xl font-bold tabular-nums amt-in">{won(confirmedSum)}</div>
              <p className="note mt-1">확정 정산액 합계 · 순매출 {won(netSum)}</p>
            </section>

            {rows.length === 0 ? (
              <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
                이 기간에는 정산 내역이 없어요. 일 정산은 매일 00:10에 전날 매출로 만들어져요.
              </p>
            ) : (
              <>
                <h2 className="px-1.5 pt-2 text-base font-medium">정산 내역</h2>
                <ul className="max-h-[55vh] space-y-2.5 overflow-y-auto overscroll-contain p-0.5">
                  {pageRows.map((d) => {
                    const open = openDate === d.settlementDate;
                    return (
                      <li key={d.settlementDate} className={`overflow-hidden rounded-xl border-2 transition ${open ? "border-brand-600 bg-brand-50/40" : "border-slate-200 bg-white"}`}>
                        <button type="button" onClick={() => setOpenDate(open ? null : d.settlementDate)} aria-expanded={open} className="flex w-full items-center justify-between gap-3 px-4 py-4 text-left active:bg-slate-50">
                          <span className="min-w-0">
                            <span className="block text-[15px] font-semibold text-slate-900">{d.settlementDate}</span>
                            <span className="mt-0.5 block text-[17px] font-bold tabular-nums text-brand-700">{won(d.confirmedAmount)}</span>
                          </span>
                          <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-brand-600 px-3.5 py-2 text-[13px] font-bold text-white">
                            {open ? "접기" : "상세 보기"}
                            <span aria-hidden className="text-[15px] leading-none">{open ? "▴" : "▾"}</span>
                          </span>
                        </button>
                        {open && <div className="border-t border-brand-200 bg-white px-4 py-4"><SettlementBreakdown d={d} /></div>}
                      </li>
                    );
                  })}
                </ul>
                {pageCount > 1 && (
                  <nav className="flex items-center justify-between pt-1" aria-label="정산 내역 페이지">
                    <button type="button" onClick={() => go(current - 1)} disabled={current === 0} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">이전</button>
                    <span className="note tabular-nums">{current + 1} / {pageCount}</span>
                    <button type="button" onClick={() => go(current + 1)} disabled={current >= pageCount - 1} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">다음</button>
                  </nav>
                )}
                <p className="note">{NEGATIVE_NOTE}</p>
              </>
            )}
          </>
        )}
      </div>
    </>
  );
}
