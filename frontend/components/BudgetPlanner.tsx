"use client";

import { useState } from "react";
import { getAnnualBudget, listMonthlyBudgets, putAnnualBudget, putMonthlyBudget, transferBudget, type AnnualBudget, type MonthlyBudget } from "@/lib/adminApi";
import { pctText } from "@/lib/cashbackApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

const MONTH_LABELS = ["1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"];
const digits = (v: string) => Number(v.replace(/\D/g, "")) || 0;
const pct = (n: number, whole: number) => (whole > 0 ? Math.round((n / whole) * 100) : 0);
const totalOf = (m: MonthlyBudget) => m.allocatedAmount + m.carriedInAmount - m.carriedOutAmount;

type Props = { year: number; thisMonth: number; contingencyRate?: number; onFlash: (message: string) => void; onChanged?: () => void };

export default function BudgetPlanner({ year, thisMonth, contingencyRate, onFlash, onChanged }: Props) {
  const annualLoad = useLoad(() => getAnnualBudget(year), [year]);
  const monthlyLoad = useLoad(() => listMonthlyBudgets(year), [year]);
  const reload = () => { annualLoad.reload(); monthlyLoad.reload(); };

  if (annualLoad.loading || monthlyLoad.loading) return <p className="note">예산을 불러오는 중…</p>;
  if (annualLoad.error || monthlyLoad.error) {
    return (
      <div className="space-y-2">
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{annualLoad.error ?? monthlyLoad.error}</p>
        <button type="button" className="btn btn-outline btn-sm !w-auto" onClick={reload}>다시 불러오기</button>
      </div>
    );
  }
  // 서버 값이 바뀌면 key 가 달라져 입력칸이 서버 값으로 다시 시작한다
  const version = JSON.stringify([annualLoad.data, monthlyLoad.data]);
  return (
    <Planner
      key={version}
      year={year}
      thisMonth={thisMonth}
      contingencyRate={contingencyRate}
      savedAnnual={annualLoad.data}
      monthly={monthlyLoad.data ?? []}
      onFlash={onFlash}
      onSaved={() => { reload(); onChanged?.(); }}
    />
  );
}

type PlannerProps = {
  year: number; thisMonth: number; contingencyRate?: number; savedAnnual: AnnualBudget | null; monthly: MonthlyBudget[];
  onFlash: (message: string) => void; onSaved: () => void;
};

function Planner({ year, thisMonth, contingencyRate, savedAnnual, monthly, onFlash, onSaved }: PlannerProps) {
  const [annual, setAnnual] = useState(savedAnnual?.totalAmount ?? 0);
  const [alloc, setAlloc] = useState<number[]>(() => {
    const next = Array(12).fill(0) as number[];
    for (const m of monthly) next[m.budgetMonth - 1] = m.allocatedAmount;
    return next;
  });
  const [baseRate, setBaseRate] = useState("");
  const [month, setMonth] = useState(thisMonth);
  const [moveAmount, setMoveAmount] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const byMonth = new Map(monthly.map((m) => [m.budgetMonth, m]));
  // 지난 달이거나 예약·지급이 시작된 달은 서버가 배정 변경을 거절한다
  const isLocked = (i: number) => {
    const m = byMonth.get(i + 1);
    return i + 1 < thisMonth || (!!m && m.reservedAmount + m.paidAmount > 0);
  };

  const sum = alloc.reduce((a, b) => a + b, 0);
  const unassigned = annual - sum;
  const over = unassigned < 0;
  const hasAnnual = !!savedAnnual;

  const cur = byMonth.get(month);
  const assigned = cur ? totalOf(cur) : 0;
  const spent = cur?.paidAmount ?? 0;
  const reserved = cur?.reservedAmount ?? 0;
  const left = cur?.remainingAmount ?? 0;
  const reserve = cur?.contingencyAmount ?? 0;
  const usedPct = pct(spent, assigned);
  const reservedPct = pct(reserved, assigned);

  const setOne = (i: number, v: number) => setAlloc((a) => a.map((x, idx) => (idx === i ? v : x)));
  const fillEven = () => {
    const editable = alloc.map((_, i) => i).filter((i) => !isLocked(i));
    if (editable.length === 0) return;
    const locked = alloc.reduce((s, v, i) => (isLocked(i) ? s + v : s), 0);
    const share = Math.max(0, Math.floor((annual - locked) / editable.length / 10_000) * 10_000);
    setAlloc((a) => a.map((v, i) => (isLocked(i) ? v : share)));
  };

  const run = async (job: () => Promise<unknown>, done: string) => {
    setBusy(true);
    setError("");
    try {
      await job();
      onFlash(done);
      onSaved();
    } catch (e) {
      setError(e instanceof Error ? e.message : "처리하지 못했어요");
    } finally {
      setBusy(false);
    }
  };

  const saveAnnual = () => run(() => putAnnualBudget(year, annual), `${year}년 연 예산이 저장되었습니다`);

  const saveAlloc = () => {
    const rate = baseRate.trim() === "" ? undefined : Number(baseRate) / 100;
    if (rate !== undefined && (Number.isNaN(rate) || rate <= 0 || rate > 1)) { setError("기본 요율은 0보다 크고 100 이하인 숫자로 입력해 주세요"); return; }
    // 바뀐 달만 보낸다. 줄이는 달을 먼저 처리해야 합계가 연 예산을 넘지 않는다
    const changes = alloc
      .map((v, i) => ({ i, v, old: byMonth.get(i + 1)?.allocatedAmount ?? 0, exists: byMonth.has(i + 1) }))
      .filter((c) => !isLocked(c.i) && c.v !== c.old && (c.exists || c.v > 0))
      .sort((a, b) => a.v - a.old - (b.v - b.old));
    if (changes.length === 0) { onFlash("바뀐 배정이 없습니다"); return; }
    void run(async () => { for (const c of changes) await putMonthlyBudget(year, c.i + 1, c.v, rate); }, `${changes.length}개 달의 예산 배정이 저장되었습니다`);
  };

  const move = () => run(() => transferBudget(year, month, moveAmount), `${MONTH_LABELS[month - 1]}의 남은 예산 ${won(moveAmount)}을 다음 달로 이월했습니다`);

  return (
    <div className="space-y-6">
      {!hasAnnual && (
        <p role="status" className="rounded-lg bg-amber-50 px-3 py-2 text-[13px] font-medium text-amber-800">
          {year}년 연 예산이 아직 없어요. 연 예산을 먼저 저장해야 월별로 배정할 수 있고, 배정이 없는 달의 결제는 적립이 제외돼요.
        </p>
      )}

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

        {!cur ? (
          <p className="note rounded-lg bg-slate-50 px-3 py-4 text-center">{MONTH_LABELS[month - 1]}은 아직 배정된 예산이 없어요. 아래 월별 배정에서 금액을 넣고 저장해 주세요.</p>
        ) : (
          <>
            <div className="sign mx-auto max-w-md !py-3 text-center">
              <div className="text-[13px] font-medium text-white/85">{MONTH_LABELS[month - 1]} 예산</div>
              <div className="text-[28px] font-extrabold tabular-nums tracking-tight">{won(assigned)}</div>
              <div className="relative mx-auto mt-2 flex h-2.5 w-full overflow-hidden rounded-full bg-white/25" role="img" aria-label={`집행 ${usedPct}%, 확보 ${reservedPct}%, 남음 ${Math.max(0, 100 - usedPct - reservedPct)}%`}>
                <span className="h-full bg-white" style={{ width: `${Math.min(100, usedPct)}%` }} />
                <span className="h-full bg-amber-500" style={{ width: `${Math.min(100 - Math.min(100, usedPct), reservedPct)}%` }} />
                <span className="absolute inset-y-0 w-0.5 bg-white" style={{ left: `${100 - pct(reserve, assigned)}%` }} />
              </div>
              <div className="mt-1 flex justify-between text-[11px] text-white/80"><span>0</span><span>대비금 {won(reserve)}</span><span>100%</span></div>
            </div>

            <div className="mx-auto h-5 w-px bg-slate-300" aria-hidden />
            <div className="relative grid grid-cols-3 pt-5">
              <div className="absolute left-[16.666%] right-[16.666%] top-0 h-px bg-slate-300" aria-hidden />
              <FlowCard label="집행 완료" value={spent} share={usedPct} tone="text-blue-600" />
              <FlowCard label="예약 · 확보" value={reserved} share={reservedPct} tone="text-amber-800" />
              <FlowCard label="남은 예산" value={left} share={pct(left, assigned)} tone={left < 0 ? "text-red-600" : "text-emerald-600"} note={left < 0 ? "배정 부족" : undefined} />
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
              <span className="text-slate-600">남은 예산을 다음 달로</span>
              <input className="field !w-36 text-right tabular-nums" inputMode="numeric" value={moveAmount.toLocaleString("ko-KR")} onChange={(e) => setMoveAmount(digits(e.target.value))} aria-label="이월 금액" />
              <span>원</span>
              <button type="button" disabled={busy || moveAmount <= 0 || moveAmount > left} onClick={move} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">예산 이월</button>
            </div>
          </>
        )}
      </section>

      <section className="space-y-3" aria-label="월별 예산 배정">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h3 className="font-bold">월별 예산 배정</h3>
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="text-slate-600">{year}년 연 예산</span>
            <input className="field !w-40 text-right font-bold tabular-nums" inputMode="numeric" value={annual.toLocaleString("ko-KR")} onChange={(e) => setAnnual(digits(e.target.value))} aria-label="연 예산" />
            <span>원</span>
            <button type="button" disabled={busy || annual <= 0 || annual === savedAnnual?.totalAmount} onClick={saveAnnual} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">연 예산 저장</button>
          </div>
        </div>

        <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
          {MONTH_LABELS.map((label, i) => {
            const locked = isLocked(i);
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
          <div className="rounded-lg bg-slate-50 px-3 py-2"><dt className="note">배정 합계</dt><dd className="font-bold tabular-nums">{won(sum)}</dd></div>
          <div className="rounded-lg bg-slate-50 px-3 py-2"><dt className="note">연 예산</dt><dd className="font-bold tabular-nums">{won(annual)}</dd></div>
          <div className={`rounded-lg px-3 py-2 ${over ? "bg-red-50" : "bg-emerald-50"}`}>
            <dt className="note">{over ? "초과 배정" : "미배정"}</dt>
            <dd className={`font-bold tabular-nums ${over ? "text-red-700" : "text-emerald-700"}`}>{won(Math.abs(unassigned))}</dd>
          </div>
        </dl>
        {over && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">월 배정 합계가 연 예산을 넘었어요. 달별 금액을 줄여 주세요.</p>}
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{error}</p>}

        <label className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-slate-600">새로 배정하는 달의 기본 요율</span>
          <input className="field !w-20 text-right tabular-nums" inputMode="decimal" placeholder="이전 달" value={baseRate} onChange={(e) => setBaseRate(e.target.value.replace(/[^\d.]/g, ""))} aria-label="기본 요율(%)" />
          <span>%</span>
          <span className="note">비우면 이전 달 값을 이어받아요. 처음 배정하는 해에는 꼭 입력해야 해요.</span>
        </label>

        <div className="flex flex-wrap gap-2">
          <button type="button" disabled={busy || over || !hasAnnual} onClick={saveAlloc} className="btn btn-primary btn-sm !w-auto disabled:opacity-40">배정 저장</button>
          <button type="button" disabled={busy} onClick={fillEven} className="btn btn-outline btn-sm !w-auto">고정되지 않은 달에 균등 배분</button>
        </div>
        <p className="note">지난 달과 예약 · 지급이 시작된 달은 배정을 바꿀 수 없어요. 대비금은 월 배정의 {contingencyRate != null ? pctText(contingencyRate) : "일부"}로 월 시작 시 확정됩니다. 예약 · 차감 · 복원은 결제 시 자동 반영됩니다.</p>
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
