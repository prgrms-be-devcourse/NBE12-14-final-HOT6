"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import LimitSettings from "@/components/LimitSettings";
import { BasisGrid, type BasisValues } from "@/components/PolicyBasisDetail";
import PolicyHistoryTable from "@/components/PolicyHistoryTable";
import Tabs, { useTab, type TabItem } from "@/components/Tabs";
import RateSimulator from "@/components/RateSimulator";
import { ApiError } from "@/lib/api";
import { getBudgetSeries, getCashbackRules, getForecast, getMonthlyBudget, getPolicies, REASON_MAX, reduceRate, thisYearMonth, type Forecast, type MonthlyBudget } from "@/lib/adminApi";
import { pctText } from "@/lib/cashbackApi";
import { useLoad } from "@/lib/useLoad";

type TabId = "rate" | "limits" | "history";
const TABS: TabItem<TabId>[] = [
  { id: "rate", label: "적립률" },
  { id: "limits", label: "한도" },
  { id: "history", label: "변경 이력" },
];

// 서버가 근거로 남기는 항목과 같게 맞춘다
const toBasis = (b: MonthlyBudget, f: Forecast): BasisValues => ({
  allocatedAmount: b.allocatedAmount, paidAmount: b.paidAmount, reservedAmount: b.reservedAmount,
  remainingAmount: b.remainingAmount, contingencyAmount: b.contingencyAmount, dailyDemand: f.assumptions.baseDailyDemand,
  fastExhaustionDate: f.fast.exhaustionDate, midExhaustionDate: f.mid.exhaustionDate, slowExhaustionDate: f.slow.exhaustionDate,
  noticeLineDate: f.mid.noticeLineDate,
});

function PolicyBody() {
  const [tab, setTab] = useTab(TABS);
  const [saved, setSaved] = useState("");
  const [rateInput, setRate] = useState<string | null>(null);
  const { year, month } = thisYearMonth();
  const now = new Date();
  const today = now.getDate();
  const daysInMonth = new Date(year, month, 0).getDate();
  const pad = (n: number) => String(n).padStart(2, "0");
  // 이번 달 월 예산과 일별 남은 예산(서버 값). 월 예산이 없으면(404) null
  const budget = useLoad(
    () => getMonthlyBudget(year, month).catch((e: unknown) => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
    [year, month],
  );
  const series = useLoad(
    () => getBudgetSeries(`${year}-${pad(month)}-01`, `${year}-${pad(month)}-${pad(today)}`),
    [year, month, today],
  );
  // 안내 문구에 쓰는 서버 설정값. 못 불러오면 숫자 없이 안내한다
  const rulesLoad = useLoad(() => getCashbackRules(), []);
  // 아직 시작하지 않은 요율 구간(예정된 인하)
  const policies = useLoad(() => getPolicies(), []);
  const nowKst = new Date().toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).replace(" ", "T");
  const upcoming = policies.data?.filter((p) => p.startsAt > nowKst).sort((a, b) => a.startsAt.localeCompare(b.startsAt)) ?? [];
  const rules = rulesLoad.data;
  // 저장할 때 서버가 남기는 것과 같은 이번 달 예산과 예측(최근 7일 기준). 이번 달 예산이 없으면 null
  const basis = useLoad(
    () => Promise.all([getMonthlyBudget(year, month), getForecast()]).catch((e: unknown) => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
    [year, month],
  );
  const monthSeries = series.data?.find((s) => s.year === year && s.month === month);
  // 요율 입력의 시작값은 이번 달 기본 요율이다(직접 바꾸기 전까지)
  const rate = rateInput ?? (budget.data?.baseRate != null ? String(Math.round(budget.data.baseRate * 1000) / 10) : "");
  const [from, setFrom] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [apiError, setApiError] = useState("");
  const r = Number(rate);
  // 달 중에는 인하만 한다. 현재(월 기본) 요율보다 낮아야 저장할 수 있다
  const currentRate = budget.data?.baseRate != null ? Math.round(budget.data.baseRate * 1000) / 10 : null;
  const notLower = currentRate != null && r >= currentRate;
  const policyError =
    !rate.trim() || Number.isNaN(r) || r <= 0 || r > 100 ? "요율은 0보다 크고 100 이하인 숫자로 입력해 주세요"
    : notLower && rateInput !== null ? `요율은 현재 요율(${currentRate}%)보다 낮아야 해요`
    : !from ? "적용 시작 일시를 입력해 주세요"
    : !reason.trim() ? "인하 사유를 입력해 주세요"
    : "";
  // 손대지 않은 빈 양식에는 오류를 보여 주지 않는다
  const touched = rateInput !== null || from !== "" || reason !== "";
  const formError = apiError || (touched ? policyError : "");
  const flash = (m: string) => { setSaved(m); setTimeout(() => setSaved(""), 3000); };
  const run = async (job: () => Promise<unknown>, done: string) => {
    setApiError("");
    try { await job(); flash(done); } catch (e) { setApiError(e instanceof Error ? e.message : "처리하지 못했어요"); }
  };
  const save = async () => {
    setBusy(true);
    // 저장에 성공했을 때만 양식을 비운다
    await run(async () => { await reduceRate(r / 100, `${from}:00`, reason.trim()); setRate(null); setFrom(""); setReason(""); }, `요율 ${r}% (${from.replace("T", " ")}부터) 인하가 저장되었습니다 · 정책 변경 예고 공지가 등록됩니다`);
    policies.reload();
    basis.reload();
    setBusy(false);
  };

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-extrabold">정책 관리</h1>
      <Tabs items={TABS} active={tab} onChange={setTab} label="정책 관리 묶음" />
      {saved && <p role="status" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white">{saved}</p>}

      {/* 입력하던 값이 탭을 오가도 남도록 숨기기만 한다 */}
      <section className="card !p-5" hidden={tab !== "rate"}>
        <h2 className="mb-4 text-lg font-bold">적립률 인하 · 시뮬레이션</h2>
        {upcoming.map((p) => (
          <p key={p.policyId} className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-[13px] font-medium text-amber-900">
            <b>예약됨</b> · {Number(p.startsAt.slice(5, 7))}월 {Number(p.startsAt.slice(8, 10))}일 {p.startsAt.slice(11, 16)}부터 <b className="tabular-nums">{pctText(p.rate)}</b>
          </p>
        ))}
        {budget.loading || series.loading || rulesLoad.loading ? (
          <p className="note">이번 달 예산을 불러오는 중…</p>
        ) : budget.error || series.error || rulesLoad.error ? (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{budget.error ?? series.error ?? rulesLoad.error}</p>
        ) : !budget.data || !rules ? (
          <p className="note rounded-lg bg-amber-50 px-3 py-3 text-amber-800">{month}월 예산이 아직 배정되지 않았어요. <Link href="/admin/budget" className="font-bold underline">예산 관리</Link>에서 이번 달을 배정하면 요율 시뮬레이션을 볼 수 있어요.</p>
        ) : (
          <RateSimulator rate={rate} onRate={setRate} budget={budget.data} points={monthSeries?.points ?? []} today={today} daysInMonth={daysInMonth} noticeDays={rules.noticeDays} emergencyRatio={rules.emergencyRatio} />
        )}
        <div className="mt-5 space-y-3 border-t border-slate-200 pt-5">
          <h3 className="font-bold">정책 저장</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block"><span className="label">적용 시작 일시</span><input className="field" type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          </div>
          <div className="space-y-3 rounded-lg bg-slate-50 px-3 py-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-bold">저장할 때 기록되는 근거 <span className="note font-normal">최근 7일 수요 기준 · 저장하는 순간의 값이 남아요</span></p>
              <button type="button" onClick={basis.reload} disabled={basis.loading} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">새로고침</button>
            </div>
            {basis.loading ? (
              <p className="note">근거를 불러오는 중…</p>
            ) : basis.error ? (
              <p className="note">{basis.error}</p>
            ) : !basis.data ? (
              <p className="note">이번 달 예산이 없어 직전 요율만 남아요.</p>
            ) : (
              <BasisGrid basis={toBasis(...basis.data)} />
            )}
          </div>
          <label className="block">
            <span className="label">인하 사유 <span className="note tabular-nums">{reason.length}/{REASON_MAX}</span></span>
            <textarea className="field min-h-20" maxLength={REASON_MAX} placeholder="예: 최근 결제가 늘어 이번 달 예산이 일찍 소진될 것으로 보여요" value={reason} onChange={(e) => setReason(e.target.value)} />
            <span className="note">운영자만 보는 기록이에요. 지금의 예산과 소진 예측이 함께 변경 이력에 남아요.</span>
          </label>
          {formError && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{formError}</p>}
          <button type="button" disabled={!!policyError || notLower || busy} onClick={save} className="btn btn-primary !w-auto px-8 disabled:opacity-40">요율 인하 저장</button>
          <p className="note">달 중에는 요율을 인하만 할 수 있고(올리려면 다음 달 기본 요율로 정해요), 저장하면 정책 변경 예고 공지가 자동으로 등록돼요. 적용은 그 달 말까지예요.</p>
        </div>
      </section>

      <section className="space-y-3" hidden={tab !== "limits"} aria-label="한도">
        <div className="card space-y-4 !p-5"><LimitSettings onSaved={flash} /></div>
      </section>
      {/* 이력은 바꾼 직후의 값을 보이도록 열 때마다 새로 읽는다 */}
      {tab === "history" && <PolicyHistoryTable />}
    </div>
  );
}

// 정책 관리: 캐시백 요율 인하, 한도 변경, 변경 이력. 예산 배정과 적립 중단은 예산 관리에서 한다
export default function AdminPolicyPage() {
  return (
    <Suspense fallback={null}>
      <PolicyBody />
    </Suspense>
  );
}
