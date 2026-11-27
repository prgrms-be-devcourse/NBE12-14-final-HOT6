"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import LimitSettings from "@/components/LimitSettings";
import PolicyHistoryTable from "@/components/PolicyHistoryTable";
import Tabs, { useTab, type TabItem } from "@/components/Tabs";
import RateReductionModal from "@/components/RateReductionModal";
import RateSimulator from "@/components/RateSimulator";
import { ApiError } from "@/lib/api";
import { getBudgetSeries, getCashbackRules, getMonthlyBudget, getPolicies, thisYearMonth, type Policy } from "@/lib/adminApi";
import { pctText } from "@/lib/cashbackApi";
import { useLoad } from "@/lib/useLoad";

type TabId = "rate" | "limits" | "history";
const TABS: TabItem<TabId>[] = [
  { id: "rate", label: "적립률" },
  { id: "limits", label: "한도" },
  { id: "history", label: "변경 이력" },
];

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
  const monthSeries = series.data?.find((s) => s.year === year && s.month === month);
  // 요율 입력의 시작값은 이번 달 기본 요율이다(직접 바꾸기 전까지)
  const rate = rateInput ?? (budget.data?.baseRate != null ? String(Math.round(budget.data.baseRate * 1000) / 10) : "");
  // 열린 인하 창. "new" 는 새 인하, 구간이면 그 예약의 수정
  const [modal, setModal] = useState<"new" | Policy | null>(null);
  // 달 중에는 인하만 한다. 현재(월 기본) 요율보다 낮아야 저장할 수 있다
  const currentRate = budget.data?.baseRate != null ? Math.round(budget.data.baseRate * 1000) / 10 : null;
  const flash = (m: string) => { setSaved(m); setTimeout(() => setSaved(""), 3000); };
  // 예약 구간의 바로 앞 요율(앞 구간이 없으면 이번 달 기본 요율). 다른 달의 예약은 서버가 확인한다
  const previousRate = (p: Policy) => {
    const prev = policies.data?.find((o) => o.policyId !== p.policyId && o.endsAt === p.startsAt);
    if (prev) return Math.round(prev.rate * 1000) / 10;
    return Number(p.startsAt.slice(5, 7)) === month ? currentRate : null;
  };
  const done = (message: string) => {
    // 새로 인하했으면 시뮬레이터는 다시 현재 요율에서 시작한다
    if (modal === "new") setRate(null);
    setModal(null);
    flash(message);
    policies.reload();
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
          <div key={p.policyId} className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2">
            <p className="text-[13px] font-medium text-amber-900">
              <b>예약됨</b> · {Number(p.startsAt.slice(5, 7))}월 {Number(p.startsAt.slice(8, 10))}일 {p.startsAt.slice(11, 16)}부터 <b className="tabular-nums">{pctText(p.rate)}</b>
            </p>
            <button type="button" onClick={() => setModal(p)} className="btn btn-outline btn-sm !w-auto">수정</button>
          </div>
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
        <div className="mt-5 space-y-2 border-t border-slate-200 pt-5">
          <button type="button" onClick={() => setModal("new")} disabled={!budget.data} className="btn btn-primary !w-auto px-8 disabled:opacity-40">적립률 인하하기</button>
          <p className="note">달 중에는 요율을 인하만 할 수 있어요(올리려면 다음 달 기본 요율로 정해요). 인하할 때 알릴 공지를 함께 작성하고, 적용은 그 달 말까지예요.</p>
        </div>
      </section>

      <section className="space-y-3" hidden={tab !== "limits"} aria-label="한도">
        <div className="card space-y-4 !p-5"><LimitSettings onSaved={flash} /></div>
      </section>
      {/* 이력은 바꾼 직후의 값을 보이도록 열 때마다 새로 읽는다 */}
      {tab === "history" && <PolicyHistoryTable />}
      {modal && (
        <RateReductionModal
          policyId={modal === "new" ? null : modal.policyId}
          // 시뮬레이터에서 고른 값이 현재보다 낮을 때만 미리 넣는다
          initialRate={currentRate != null && Number(rate) > 0 && Number(rate) < currentRate ? rate : ""}
          maxRate={modal === "new" ? currentRate : previousRate(modal)}
          onClose={() => setModal(null)}
          onSaved={done}
        />
      )}
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
