"use client";

import Link from "next/link";
import BarChart from "@/components/BarChart";
import ForecastAccuracyTable from "@/components/ForecastAccuracyTable";
import { ApiError } from "@/lib/api";
import {
  getAdminNotices, getForecast, getMonthlyBudget, getPayoutStatus, getPolicies, getSettings, md, moneyUnit, STATUS_TEXT, thisYearMonth,
} from "@/lib/adminApi";
import { LIMIT_NAME, mmdd, pctText } from "@/lib/cashbackApi";
import { ymd } from "@/lib/dates";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

const NOTICE_SAMPLE = 100;
// 지금(한국 시간)을 서버 시각 문자열과 비교할 수 있는 "yyyy-MM-ddTHH:mm" 으로 만든다
const nowKst = () => new Date().toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).replace(" ", "T").slice(0, 16);

// 한눈에 보는 현황판: 이번 달 예산, 소진 예측, 지금 적용 중인 정책, 확인할 일. 바꾸는 일은 각 관리 화면에서 한다
export default function AdminDashboardPage() {
  // 이번 달 월 예산이 아직 없으면(CSH-004) 오류 대신 배정 화면 링크를 보여 준다
  const budget = useLoad(() => {
    const t = thisYearMonth();
    return getMonthlyBudget(t.year, t.month).catch((e: unknown) => {
      if (e instanceof ApiError && e.code === "CSH-004") return null;
      throw e;
    });
  }, []);
  const forecast = useLoad(getForecast, []);
  const policies = useLoad(getPolicies, []);
  const settings = useLoad(getSettings, []);
  const payouts = useLoad(getPayoutStatus, []);
  const notices = useLoad(() => getAdminNotices(0, NOTICE_SAMPLE), []);

  const b = budget.data;
  const f = forecast.data;
  const pts = f?.points.slice(0, 7) ?? [];
  // 작은 예산도 보이도록 가장 큰 값에 맞춰 원·만원 단위를 고른다
  const unit = moneyUnit(Math.max(f?.contingencyAmount ?? 0, ...pts.flatMap((p) => [p.fastRemaining, p.midRemaining, p.slowRemaining])));

  const now = nowKst();
  const current = policies.data?.find((p) => p.startsAt.slice(0, 16) <= now && (!p.endsAt || now < p.endsAt.slice(0, 16)));
  const upcoming = policies.data?.filter((p) => p.startsAt.slice(0, 16) > now).sort((a, c) => a.startsAt.localeCompare(c.startsAt))[0];
  const currentRate = current?.rate ?? b?.baseRate ?? null;

  const today = ymd(new Date());
  const onHome = notices.data?.content.filter((n) => n.status === "PUBLISHED" && n.showOnHome && (n.homeUntil ?? "") >= today).length;
  const rateCount = upcoming ? 1 : 0;
  const limitCount = settings.data?.filter((s) => s.scheduled).length;

  const total = b ? b.allocatedAmount + b.carriedInAmount - b.carriedOutAmount : 0;
  const share = (v: number) => (total > 0 ? Math.min(100, Math.max(0, (v / total) * 100)) : 0);
  const kpi = b && [
    { k: "남은 예산", v: won(b.remainingAmount), c: "text-emerald-600", tag: "남음", tagCls: "badge-ok" },
    { k: "집행 완료", v: won(b.paidAmount), c: "text-blue-600", tag: "완료", tagCls: "badge-info" },
    { k: "예약 · 확보", v: won(b.reservedAmount), c: "text-amber-800", tag: "확보", tagCls: "badge-warn" },
  ];
  const errors = [budget.error, policies.error, settings.error].filter(Boolean);

  const todos = [
    { k: "지금 지급 가능", v: payouts.data ? `${payouts.data.dueCount.toLocaleString("ko-KR")}건` : "…", sub: payouts.data ? won(payouts.data.dueAmount) : "", href: "/admin/earnings?tab=payouts" },
    {
      k: "예약된 변경", v: limitCount != null && policies.data ? `${rateCount + limitCount}건` : "…",
      sub: limitCount != null && policies.data ? `적립률 ${rateCount} · 한도 ${limitCount}` : "",
      href: rateCount > 0 ? "/admin/policy?tab=rate" : "/admin/policy?tab=limits",
    },
    { k: "홈 팝업 공지", v: onHome != null ? `${onHome}건` : "…", sub: "게시 중", href: "/admin/notices" },
  ];

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-extrabold">
        운영팀 대시보드 <span className="text-base font-normal text-slate-500">{b ? `${b.budgetYear}년 ${b.budgetMonth}월 · ${STATUS_TEXT[b.status]}` : ""}</span>
      </h1>
      {errors.length > 0 && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{errors[0]}</p>}
      {!budget.loading && !budget.error && !b && (
        <div role="alert" className="rounded-xl border border-amber-500 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          이번 달 예산이 아직 없어요. <Link href="/admin/budget" className="font-bold underline">예산 관리에서 배정하기</Link>
        </div>
      )}
      {b && b.warning !== "NONE" && (
        <div role="alert" className="rounded-xl border border-amber-500 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <b>주의</b> {b.warning === "EMERGENCY_LINE" ? "남은 예산이 긴급선 이하입니다. 즉시 중단을 검토하세요." : "남은 예산이 대비금 기준 이하입니다. 공지 후 중단을 검토하세요."}{" "}
          <Link href="/admin/budget?tab=stop" className="font-bold underline">적립 중단 보기</Link>
        </div>
      )}

      {kpi && (
        <section className="card space-y-4 !p-5" aria-label="이번 달 예산">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-lg font-bold">이번 달 예산 <span className="tabular-nums">{won(total)}</span></h2>
            <Link href="/admin/budget" className="text-[13px] font-medium text-brand-600 underline">예산 관리</Link>
          </div>
          <div className="flex h-3 w-full overflow-hidden rounded-full bg-emerald-100" role="img" aria-label={`집행 ${Math.round(share(b.paidAmount))}%, 확보 ${Math.round(share(b.reservedAmount))}%`}>
            <span className="h-full bg-blue-600" style={{ width: `${share(b.paidAmount)}%` }} />
            <span className="h-full bg-amber-500" style={{ width: `${share(b.reservedAmount)}%` }} />
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            {kpi.map((x) => <div key={x.k} className="rounded-lg bg-slate-50 px-3 py-2.5"><div className="flex items-center justify-between"><span className="note">{x.k}</span><span className={`badge ${x.tagCls}`}>{x.tag}</span></div><div className={`mt-1 text-2xl font-bold tabular-nums ${x.c}`}>{x.v}</div></div>)}
          </div>
        </section>
      )}

      <section className="card !p-5">
        <h2 className="mb-3 text-lg font-bold">소진 예측 <span className="note">(최근 7일 수요 기반{f ? ` · 모델 ${f.assumptions.modelVersion}` : ""})</span></h2>
        {forecast.error && <p className="note">{forecast.error}</p>}
        {f && pts.length > 0 && (
          <BarChart
            label="소진 예측"
            labels={pts.map((p) => md(p.date))}
            reserveLine={f.contingencyAmount / unit.div}
            series={[
              { name: `빠름(${unit.label})`, color: "var(--color-blue-300)", values: pts.map((p) => p.fastRemaining / unit.div) },
              { name: `중앙(${unit.label})`, color: "var(--color-blue-600)", values: pts.map((p) => p.midRemaining / unit.div) },
              { name: `느림(${unit.label})`, color: "var(--color-blue-900)", values: pts.map((p) => p.slowRemaining / unit.div) },
            ]}
          />
        )}
        {f && (
          <dl className="mt-4 grid gap-3 sm:grid-cols-3">
            {([
              ["예상 소진일 (빠름 / 중앙 / 느림)", `${md(f.fast.exhaustionDate)} / ${md(f.mid.exhaustionDate)} / ${md(f.slow.exhaustionDate)}`],
              ["대비금 도달일 (중앙)", md(f.mid.noticeLineDate)],
              ["최소 보장 인원", `${f.guaranteedUsers.toLocaleString("ko-KR")}명`],
            ] as const).map(([k, v]) => (
              <div key={k} className="rounded-lg bg-slate-50 px-3 py-2"><dt className="note">{k}</dt><dd className="mt-0.5 font-bold tabular-nums">{v}</dd></div>
            ))}
          </dl>
        )}
        <ForecastAccuracyTable />
      </section>

      <section className="card !p-5">
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-lg font-bold">지금 적용 중인 정책</h2>
          <Link href="/admin/policy" className="text-[13px] font-medium text-brand-600 underline">정책 관리</Link>
        </div>
        <table className="table">
          <thead><tr><th>항목</th><th>현재 값</th><th>예정</th></tr></thead>
          <tbody>
            <tr>
              <td>적립률</td>
              <td className="font-bold tabular-nums">{currentRate != null ? pctText(currentRate) : "-"}</td>
              <td className="tabular-nums">{upcoming ? `${mmdd(upcoming.startsAt)}부터 ${pctText(upcoming.rate)}` : "-"}</td>
            </tr>
            {settings.data?.map((s) => (
              <tr key={s.key}>
                <td>{LIMIT_NAME[s.key] ?? s.description}</td>
                <td className="font-bold tabular-nums">{won(s.value)}</td>
                <td className="tabular-nums">{s.scheduled ? `${mmdd(s.scheduled.effectiveDate)}부터 ${won(s.scheduled.value)}` : "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="grid gap-3 sm:grid-cols-3" aria-label="확인할 일">
        {todos.map((t) => (
          <Link key={t.k} href={t.href} className="tile transition hover:border-brand-600">
            <div className="note">{t.k}</div>
            <div className="mt-1 text-2xl font-bold tabular-nums">{t.v}</div>
            <div className="note mt-0.5">{t.sub}</div>
          </Link>
        ))}
      </section>
    </div>
  );
}
