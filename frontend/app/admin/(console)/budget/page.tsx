"use client";

import { Suspense, useState } from "react";
import BudgetPlanner from "@/components/BudgetPlanner";
import ConfirmButton from "@/components/ConfirmButton";
import Tabs, { useTab, type TabItem } from "@/components/Tabs";
import { ApiError } from "@/lib/api";
import { emergencyStop, getCashbackRules, getMonthlyBudget, noticeStop, STATUS_TEXT, thisYearMonth } from "@/lib/adminApi";
import { pctText } from "@/lib/cashbackApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

type TabId = "month" | "stop";
const TABS: TabItem<TabId>[] = [
  { id: "month", label: "예산 현황 · 배정" },
  { id: "stop", label: "적립 중단" },
];

// 적립 중단: 이번 달 상태를 보고 공지 후 중단하거나 바로 중단한다
function StopControl({ contingencyRate, emergencyRatio, noticeDays, onFlash }: { contingencyRate?: number; emergencyRatio?: number; noticeDays?: number; onFlash: (m: string) => void }) {
  const budget = useLoad(() => {
    const t = thisYearMonth();
    return getMonthlyBudget(t.year, t.month).catch((e: unknown) => {
      if (e instanceof ApiError && e.code === "CSH-004") return null;
      throw e;
    });
  }, []);
  const [apiError, setApiError] = useState("");
  const b = budget.data;
  const noticeAndStop = async () => {
    setApiError("");
    const t = thisYearMonth();
    try {
      await noticeStop(t.year, t.month);
      onFlash(noticeDays != null ? `공지 후 중단이 예약되었습니다 (${noticeDays}일 뒤 00:00 중단)` : "공지 후 중단이 예약되었습니다");
      budget.reload();
    } catch (e) {
      setApiError(e instanceof Error ? e.message : "처리하지 못했어요");
    }
  };

  return (
    <section className="card space-y-4 !p-5">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-lg font-bold">이번 달 적립 상태</h2>
        {b && <span className={`badge ${b.status === "ACTIVE" ? "badge-ok" : b.status === "NOTICED" ? "badge-warn" : "badge-bad"}`}>{STATUS_TEXT[b.status]}</span>}
        {b && <span className="note">남은 예산 {won(b.remainingAmount)}</span>}
      </div>
      {budget.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{budget.error}</p>}
      {!budget.loading && !budget.error && !b && <p className="note rounded-lg bg-amber-50 px-3 py-3 text-amber-800">이번 달 예산이 없어 중단할 대상이 없어요.</p>}
      {b && (
        <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
          <li className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
            <div><div className="font-bold">공지 후 중단</div><p className="note">{noticeDays != null ? `${noticeDays}일 뒤 00:00에 중단하고 이용자에게 바로 알려요.` : "이용자에게 알리고 며칠 뒤 중단해요."}</p></div>
            <button type="button" disabled={b.status !== "ACTIVE"} onClick={noticeAndStop} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">공지 후 중단</button>
          </li>
          <li className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
            <div><div className="font-bold">긴급 중단</div><p className="note">적립을 바로 멈추고 중단 공지를 올려요. 결제는 계속 할 수 있어요.</p></div>
            {b.status === "ACTIVE" || b.status === "NOTICED" ? (
              <ConfirmButton label="긴급 중단" message="적립이 즉시 중단돼요. 진행할까요?" onConfirm={async () => { await emergencyStop(b.budgetYear, b.budgetMonth); onFlash("적립을 긴급 중단했어요"); budget.reload(); }} />
            ) : (
              <button type="button" disabled className="btn btn-danger btn-sm !w-auto disabled:opacity-40">긴급 중단</button>
            )}
          </li>
        </ul>
      )}
      {apiError && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{apiError}</p>}
      <p className="note">{contingencyRate != null && emergencyRatio != null ? `자동 중단 기준: 대비금은 월 배정의 ${pctText(contingencyRate)}, 남은 예산이 대비금의 ${pctText(emergencyRatio)}에 닿으면 즉시 중단` : "남은 예산이 긴급선에 닿으면 자동으로 중단해요."}</p>
    </section>
  );
}

function BudgetBody() {
  const [tab, setTab] = useTab(TABS);
  const [saved, setSaved] = useState("");
  const { year, month } = thisYearMonth();
  // 안내 문구에 쓰는 서버 설정값. 못 불러오면 숫자 없이 안내한다
  const rules = useLoad(() => getCashbackRules(), []).data;
  const flash = (m: string) => { setSaved(m); setTimeout(() => setSaved(""), 3000); };

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-extrabold">예산 관리</h1>
      <Tabs items={TABS} active={tab} onChange={setTab} label="예산 관리 묶음" />
      {saved && <p role="status" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white">{saved}</p>}
      {/* 입력하던 값이 탭을 오가도 남도록, 예산 화면은 숨기기만 한다 */}
      <div hidden={tab !== "month"}>
        <BudgetPlanner year={year} thisMonth={month} contingencyRate={rules?.contingencyRate} onFlash={flash} />
      </div>
      {tab === "stop" && <StopControl contingencyRate={rules?.contingencyRate} emergencyRatio={rules?.emergencyRatio} noticeDays={rules?.noticeDays} onFlash={flash} />}
    </div>
  );
}

// 예산 관리: 월 예산 현황·이월, 연·월 예산 배정, 적립 중단. 요율과 한도는 정책 관리에서 바꾼다
export default function AdminBudgetPage() {
  return (
    <Suspense fallback={null}>
      <BudgetBody />
    </Suspense>
  );
}
