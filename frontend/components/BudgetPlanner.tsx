"use client";

import { useState } from "react";
import { ACTIVITY, ANNUAL_BUDGET, INITIAL_ALLOCATION, MONTH_LABELS, RESERVE_RATIO } from "@/lib/budget";
import { won } from "@/lib/format";

const digits = (v: string) => Number(v.replace(/\D/g, "")) || 0;
const pct = (n: number, total: number) => (total > 0 ? Math.round((n / total) * 100) : 0);

type Props = { onFlash: (message: string) => void };

export default function BudgetPlanner({ onFlash }: Props) {
  const [annual, setAnnual] = useState(ANNUAL_BUDGET);
  const [alloc, setAlloc] = useState<number[]>(INITIAL_ALLOCATION);
  const [month, setMonth] = useState(11);

  const total = alloc.reduce((a, b) => a + b, 0);
  const unassigned = annual - total;
  const over = unassigned < 0;

  const assigned = alloc[month - 1];
  const act = ACTIVITY[month] ?? { spent: 0, reserved: 0 };
  const left = assigned - act.spent - act.reserved;
  const reserve = Math.round(assigned * RESERVE_RATIO);
  const usedPct = pct(act.spent, assigned);
  const reservedPct = pct(act.reserved, assigned);

  const setOne = (i: number, v: number) => setAlloc((a) => a.map((x, idx) => (idx === i ? v : x)));
  const fillEven = () => {
    const editable = alloc.map((_, i) => i).filter((i) => !ACTIVITY[i + 1]);
    const locked = alloc.reduce((s, v, i) => (ACTIVITY[i + 1] ? s + v : s), 0);
    const share = Math.max(0, Math.floor((annual - locked) / editable.length / 10_000) * 10_000);
    setAlloc((a) => a.map((v, i) => (ACTIVITY[i + 1] ? v : share)));
  };

  return (
    <div className="space-y-6">
      <section aria-label={`${MONTH_LABELS[month - 1]} 예산 분배`}>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-bold">{MONTH_LABELS[month - 1]} 예산 분배</h3>
          <label className="flex items-center gap-2 text-sm">
            <span className="text-slate-600">보는 달</span>
            <select className="field !w-auto !py-1.5" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
              {MONTH_LABELS.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
            </select>
          </label>
        </div>

        <div className="sign mx-auto max-w-md !py-3 text-center">
          <div className="text-[13px] font-medium text-white/85">{MONTH_LABELS[month - 1]} 예산</div>
          <div className="text-[28px] font-extrabold tabular-nums tracking-tight">{won(assigned)}</div>
          <div className="relative mx-auto mt-2 flex h-2.5 w-full overflow-hidden rounded-full bg-white/25" role="img" aria-label={`집행 ${usedPct}%, 확보 ${reservedPct}%, 남음 ${Math.max(0, 100 - usedPct - reservedPct)}%`}>
            <span className="h-full bg-white" style={{ width: `${Math.min(100, usedPct)}%` }} />
            <span className="h-full bg-amber-500" style={{ width: `${Math.min(100 - Math.min(100, usedPct), reservedPct)}%` }} />
            <span className="absolute inset-y-0 w-0.5 bg-white" style={{ left: "90%" }} />
          </div>
          <div className="mt-1 flex justify-between text-[11px] text-white/80"><span>0</span><span style={{ marginRight: "6%" }}>대비금 {won(reserve)}</span><span>100%</span></div>
        </div>

        <div className="mx-auto h-5 w-px bg-slate-300" aria-hidden />
        <div className="relative grid grid-cols-3 pt-5">
          <div className="absolute left-[16.666%] right-[16.666%] top-0 h-px bg-slate-300" aria-hidden />
          <FlowCard label="집행 완료" value={act.spent} share={usedPct} tone="text-blue-600" />
          <FlowCard label="예약 · 확보" value={act.reserved} share={reservedPct} tone="text-amber-800" />
          <FlowCard label="남은 예산" value={left} share={pct(left, assigned)} tone={left < 0 ? "text-red-600" : "text-emerald-600"} note={left < 0 ? "배정 부족" : undefined} />
        </div>
      </section>

      <section className="space-y-3" aria-label="월별 예산 배정">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h3 className="font-bold">월별 예산 배정</h3>
          <label className="flex items-center gap-2 text-sm">
            <span className="text-slate-600">연 예산</span>
            <input className="field !w-40 text-right font-bold tabular-nums" inputMode="numeric" value={annual.toLocaleString("ko-KR")} onChange={(e) => setAnnual(digits(e.target.value))} aria-label="연 예산" />
            <span>원</span>
          </label>
        </div>

        <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
          {MONTH_LABELS.map((label, i) => {
            const locked = !!ACTIVITY[i + 1];
            const selected = month === i + 1;
            return (
              <li key={label} className={`rounded-lg border p-2.5 ${selected ? "border-brand-600 bg-brand-50" : "border-slate-200 bg-white"}`}>
                <div className="mb-1 flex items-center justify-between">
                  <button type="button" onClick={() => setMonth(i + 1)} className={`text-sm font-bold ${selected ? "text-brand-700" : "text-slate-700"} hover:underline`}>{label}</button>
                  {locked && <span className="badge">고정</span>}
                </div>
                <input
                  className={`field !py-1.5 text-right text-sm font-medium tabular-nums ${locked ? "!bg-slate-100 text-slate-500" : ""}`}
                  inputMode="numeric"
                  readOnly={locked}
                  value={alloc[i].toLocaleString("ko-KR")}
                  onChange={(e) => setOne(i, digits(e.target.value))}
                  aria-label={`${label} 예산 배정`}
                />
              </li>
            );
          })}
        </ul>

        <dl className="grid gap-2 sm:grid-cols-3">
          <div className="rounded-lg bg-slate-50 px-3 py-2"><dt className="note">배정 합계</dt><dd className="font-bold tabular-nums">{won(total)}</dd></div>
          <div className="rounded-lg bg-slate-50 px-3 py-2"><dt className="note">연 예산</dt><dd className="font-bold tabular-nums">{won(annual)}</dd></div>
          <div className={`rounded-lg px-3 py-2 ${over ? "bg-red-50" : "bg-emerald-50"}`}>
            <dt className="note">{over ? "초과 배정" : "미배정"}</dt>
            <dd className={`font-bold tabular-nums ${over ? "text-red-700" : "text-emerald-700"}`}>{won(Math.abs(unassigned))}</dd>
          </div>
        </dl>
        {over && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">월 배정 합계가 연 예산을 넘었어요. 달별 금액을 줄여 주세요.</p>}

        <div className="flex flex-wrap gap-2">
          <button type="button" disabled={over} onClick={() => onFlash("월별 예산 배정이 저장되었습니다")} className="btn btn-primary btn-sm !w-auto disabled:opacity-40">배정 저장</button>
          <button type="button" onClick={fillEven} className="btn btn-outline btn-sm !w-auto">고정되지 않은 달에 균등 배분</button>
          <button type="button" onClick={() => onFlash("월 → 다음 월 예산 이월이 실행되었습니다")} className="btn btn-outline btn-sm !w-auto">예산 이월</button>
        </div>
        <p className="note">예약 · 지급이 시작된 달(9월~11월)은 배정을 바꿀 수 없어요. 대비금은 월 배정의 {RESERVE_RATIO * 100}%로 월 시작 시 확정됩니다. 예약 · 차감 · 복원은 결제 시 자동 반영됩니다.</p>
      </section>
    </div>
  );
}

function FlowCard({ label, value, share, tone, note }: { label: string; value: number; share: number; tone: string; note?: string }) {
  return (
    <div className="relative px-1.5 before:absolute before:-top-5 before:left-1/2 before:h-5 before:w-px before:bg-slate-300">
      <div className="h-full rounded-xl border border-slate-200 bg-white p-3 text-center shadow-[0_1px_2px_rgba(22,35,29,0.06)]">
        <div className="text-[13px] font-medium text-slate-600">{label}</div>
        <div className={`mt-1 text-[15px] font-extrabold tabular-nums tracking-tight sm:text-xl ${tone}`}>{won(value)}</div>
        <div className="note mt-0.5">{note ?? `월 예산의 ${share}%`}</div>
      </div>
    </div>
  );
}
