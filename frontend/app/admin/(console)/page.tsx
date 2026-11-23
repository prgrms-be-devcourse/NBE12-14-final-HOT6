"use client";

import BarChart from "@/components/BarChart";
import ConfirmButton from "@/components/ConfirmButton";
import { emergencyStop, getForecast, getMonthlyBudget, getPolicyHistory, manwon, md, STATUS_TEXT, thisYearMonth } from "@/lib/adminApi";
import { pctText } from "@/lib/cashbackApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";
import { weekdays } from "@/lib/mock";

export default function AdminDashboardPage() {
  const budget = useLoad(() => { const t = thisYearMonth(); return getMonthlyBudget(t.year, t.month); }, []);
  const forecast = useLoad(getForecast, []);
  const history = useLoad(() => getPolicyHistory(0, 10), []);
  const b = budget.data;
  const f = forecast.data;
  const pts = f?.points.slice(0, 7) ?? [];

  const kpi = b && [
    { k: "집행 완료", v: won(b.paidAmount), c: "text-blue-600", tag: "완료", tagCls: "badge-info" },
    { k: "예약 · 확보", v: won(b.reservedAmount), c: "text-amber-800", tag: "확보", tagCls: "badge-warn" },
    { k: "남은 예산", v: won(b.remainingAmount), c: "text-emerald-600", tag: "남음", tagCls: "badge-ok" },
  ];

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">
          운영팀 대시보드 <span className="text-base font-normal text-zinc-500">{b ? `${b.budgetYear}년 ${b.budgetMonth}월 · ${STATUS_TEXT[b.status]}` : ""}</span>
        </h1>
        {b && (b.status === "ACTIVE" || b.status === "NOTICED") && (
          <ConfirmButton
            label="긴급 중단"
            message="적립이 즉시 중단돼요. 진행할까요?"
            doneLabel="긴급 중단됨"
            onConfirm={async () => { await emergencyStop(b.budgetYear, b.budgetMonth); budget.reload(); }}
          />
        )}
      </div>
      {budget.error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{budget.error}</p>}
      {kpi && (
        <div className="grid gap-3 sm:grid-cols-3">
          {kpi.map((x) => <div key={x.k} className="tile"><div className="flex items-center justify-between"><span className="note">{x.k}</span><span className={`badge ${x.tagCls}`}>{x.tag}</span></div><div className={`mt-1 text-2xl font-bold tabular-nums ${x.c}`}>{x.v}</div></div>)}
        </div>
      )}
      {b && b.warning !== "NONE" && (
        <div role="alert" className="rounded-xl border border-amber-500 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <b>주의</b> {b.warning === "EMERGENCY_LINE" ? "남은 예산이 긴급선 이하입니다. 즉시 중단을 검토하세요." : "남은 예산이 대비금 기준 이하입니다. 공지 후 중단을 검토하세요."}
        </div>
      )}
      <div className="grid gap-4 lg:grid-cols-2">
        <section className="card"><h2 className="mb-3 font-semibold">소진 예측 <span className="note">(최근 7일 수요 기반{f ? ` · 모델 ${f.assumptions.modelVersion}` : ""})</span></h2>
          {forecast.error && <p className="note">{forecast.error}</p>}
          {f && pts.length > 0 && (
            <BarChart
              label="소진 예측"
              labels={pts.map((p) => md(p.date))}
              reserveLine={manwon(f.contingencyAmount)}
              series={[
                { name: "빠름(만원)", color: "var(--color-blue-300)", values: pts.map((p) => manwon(p.fastRemaining)) },
                { name: "중앙(만원)", color: "var(--color-blue-600)", values: pts.map((p) => manwon(p.midRemaining)) },
                { name: "느림(만원)", color: "var(--color-blue-900)", values: pts.map((p) => manwon(p.slowRemaining)) },
              ]}
            />
          )}
          {f && (
            <dl className="mt-3 space-y-1 text-[13px]">
              <div className="flex justify-between"><dt className="text-zinc-500">예상 소진일 (빠름 / 중앙 / 느림)</dt><dd className="font-medium">{md(f.fast.exhaustionDate)} / {md(f.mid.exhaustionDate)} / {md(f.slow.exhaustionDate)}</dd></div>
              <div className="flex justify-between"><dt className="text-zinc-500">대비금 도달일 (중앙)</dt><dd className="font-medium">{md(f.mid.noticeLineDate)}</dd></div>
              <div className="flex justify-between"><dt className="text-zinc-500">최소 보장 인원</dt><dd className="font-medium">{f.guaranteedUsers.toLocaleString("ko-KR")}명</dd></div>
            </dl>
          )}
        </section>
        <section className="card"><h2 className="mb-3 font-semibold">요율별 예상 잔액 비교 <span className="badge">예시</span></h2>
          <BarChart label="요율별 예상 잔액" labels={weekdays} series={[{ name: "요율 5%", color: "var(--color-blue-300)", values: [390, 350, 310, 270, 230, 200, 170] }, { name: "요율 10%", color: "var(--color-blue-600)", values: [380, 330, 270, 210, 150, 110, 70] }, { name: "요율 15%", color: "var(--color-blue-900)", values: [370, 300, 220, 140, 60, 10, 0] }]} />
          <p className="note mt-2">실제 계산은 MVP 이후에 연결해요. 지금은 예시 값이에요.</p></section>
      </div>
      <section className="card overflow-x-auto"><h2 className="mb-2 font-semibold">정책 변경 이력</h2>
        {history.error && <p className="note">{history.error}</p>}
        {history.data && history.data.content.length === 0 && <p className="note">변경 이력이 없어요.</p>}
        {history.data && history.data.content.length > 0 && (
          <table className="table"><thead><tr><th>일시</th><th>요율</th><th>적용 시작</th><th>비고</th></tr></thead>
            <tbody>{history.data.content.map((h) => (
              <tr key={h.historyId}>
                <td>{h.changedAt.slice(0, 16).replace("T", " ")}</td>
                <td>{h.beforeRate != null ? `${pctText(h.beforeRate)} → ` : ""}{pctText(h.afterRate)}</td>
                <td>{(h.afterStartsAt ?? h.beforeStartsAt ?? "").slice(0, 16).replace("T", " ")}</td>
                <td>{h.action === "CREATE" ? "구간 생성" : "구간 변경"}</td>
              </tr>
            ))}</tbody></table>
        )}
      </section>
    </div>
  );
}
