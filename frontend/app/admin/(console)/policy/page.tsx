"use client";

import { useState } from "react";
import BudgetPlanner from "@/components/BudgetPlanner";
import ConfirmButton from "@/components/ConfirmButton";
import RateSimulator from "@/components/RateSimulator";
import { ApiError } from "@/lib/api";
import { emergencyStop, getBudgetSeries, getCashbackRules, getMonthlyBudget, noticeStop, reduceRate, thisYearMonth } from "@/lib/adminApi";
import { pctText } from "@/lib/cashbackApi";
import { useLoad } from "@/lib/useLoad";

export default function AdminPolicyPage() {
  const [saved, setSaved] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [rateInput, setRate] = useState<string | null>(null);
  const { year, month } = thisYearMonth();
  const now = new Date();
  const today = now.getDate();
  const daysInMonth = new Date(year, month, 0).getDate();
  const pad = (n: number) => String(n).padStart(2, "0");
  // 이번 달 월 예산과 일별 남은 예산(서버 값). 월 예산이 없으면(404) null
  const budget = useLoad(
    () => getMonthlyBudget(year, month).catch((e: unknown) => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
    [year, month, reloadKey],
  );
  const series = useLoad(
    () => getBudgetSeries(`${year}-${pad(month)}-01`, `${year}-${pad(month)}-${pad(today)}`),
    [year, month, today, reloadKey],
  );
  // 안내 문구에 쓰는 서버 설정값. 못 불러오면 숫자 없이 안내한다
  const rulesLoad = useLoad(() => getCashbackRules(), []);
  const rules = rulesLoad.data;
  const monthSeries = series.data?.find((s) => s.year === year && s.month === month);
  // 요율 입력의 시작값은 이번 달 기본 요율이다(직접 바꾸기 전까지)
  const rate = rateInput ?? (budget.data?.baseRate != null ? String(Math.round(budget.data.baseRate * 1000) / 10) : "");
  const [from, setFrom] = useState("");
  const [busy, setBusy] = useState(false);
  const [apiError, setApiError] = useState("");
  const r = Number(rate);
  const policyError =
    !rate.trim() || Number.isNaN(r) || r <= 0 || r > 100 ? "요율은 0보다 크고 100 이하인 숫자로 입력해 주세요"
    : !from ? "적용 시작 일시를 입력해 주세요"
    : "";
  const flash = (m: string) => { setSaved(m); setTimeout(() => setSaved(""), 3000); };
  const run = async (job: () => Promise<unknown>, done: string) => {
    setApiError("");
    try { await job(); flash(done); } catch (e) { setApiError(e instanceof Error ? e.message : "처리하지 못했어요"); }
  };
  const save = async () => {
    setBusy(true);
    await run(() => reduceRate(r / 100, `${from}:00`), `요율 ${r}% (${from.replace("T", " ")}부터) 인하가 저장되었습니다 · 정책 변경 예고 공지가 등록됩니다`);
    setBusy(false);
  };

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-extrabold">정책 · 예산 관리</h1>
      {saved && <p role="status" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white">{saved}</p>}

      <section className="card space-y-4 !p-5">
        <h2 className="text-lg font-bold">월 예산</h2>
        <BudgetPlanner year={year} thisMonth={month} contingencyRate={rules?.contingencyRate} onFlash={flash} onChanged={() => setReloadKey((k) => k + 1)} />
      </section>

      <section className="card !p-5">
        <h2 className="mb-4 text-lg font-bold">캐시백 정책 · 요율 시뮬레이션</h2>
        {budget.loading || series.loading || rulesLoad.loading ? (
          <p className="note">이번 달 예산을 불러오는 중…</p>
        ) : budget.error || series.error || rulesLoad.error ? (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{budget.error ?? series.error ?? rulesLoad.error}</p>
        ) : !budget.data || !rules ? (
          <p className="note rounded-lg bg-amber-50 px-3 py-3 text-amber-800">{month}월 예산이 아직 배정되지 않았어요. 위 월 예산에서 이번 달을 배정하면 요율 시뮬레이션을 볼 수 있어요.</p>
        ) : (
          <RateSimulator rate={rate} onRate={setRate} budget={budget.data} points={monthSeries?.points ?? []} today={today} daysInMonth={daysInMonth} noticeDays={rules.noticeDays} emergencyRatio={rules.emergencyRatio} />
        )}
        <div className="mt-5 space-y-3 border-t border-slate-200 pt-5">
          <h3 className="font-bold">정책 저장</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block"><span className="label">적용 시작 일시</span><input className="field" type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          </div>
          {(policyError || apiError) && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{policyError || apiError}</p>}
          <button type="button" disabled={!!policyError || busy} onClick={save} className="btn btn-primary !w-auto px-8 disabled:opacity-40">요율 인하 저장</button>
          <p className="note">달 중에는 요율을 인하만 할 수 있고(올리려면 다음 달 기본 요율로 정해요), 저장하면 정책 변경 예고 공지가 자동으로 등록돼요. 적용은 그 달 말까지예요.</p>
        </div>
      </section>

      <section className="card space-y-3 !p-5">
        <h2 className="text-lg font-bold">운영 제어</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <button type="button" onClick={() => { const t = thisYearMonth(); void run(() => noticeStop(t.year, t.month), rules ? `공지 후 중단이 예약되었습니다 (${rules.noticeDays}일 뒤 00:00 중단)` : "공지 후 중단이 예약되었습니다"); }} className="btn btn-outline">공지 후 중단</button>
          <ConfirmButton label="긴급 중단" message="적립이 즉시 중단돼요. 진행할까요?" doneLabel="긴급 중단됨" className="btn btn-danger" onConfirm={() => { const t = thisYearMonth(); return emergencyStop(t.year, t.month).then(() => undefined); }} />
        </div>
        <p className="note">{rules ? `대비금 기준: 월 배정의 ${pctText(rules.contingencyRate)}, 남은 예산이 대비금의 ${pctText(rules.emergencyRatio)}에 닿으면 즉시 중단` : "대비금 기준: 월 배정의 일부를 대비금으로 두고, 남은 예산이 긴급선에 닿으면 즉시 중단"}</p>
      </section>
    </div>
  );
}
