"use client";

import { useRef, useState } from "react";
import { getBudgetSeries, md, type Forecast } from "@/lib/adminApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

const W = 720;
const H = 300;
const PAD = { l: 62, r: 20, t: 18, b: 32 };
const Y_TICKS = [0, 0.25, 0.5, 0.75, 1];
const SCENARIOS = [
  { key: "fast", label: "빠를 때", color: "#eb6834", dash: "3 3", width: 2 },
  { key: "mid", label: "중앙", color: "#2a78d6", dash: "7 4", width: 2.5 },
  { key: "slow", label: "느릴 때", color: "#1baf7a", dash: "10 3 2 3", width: 2 },
] as const;

const dayOf = (iso: string) => Number(iso.slice(8, 10));
const shortWon = (n: number) => (n < 10_000 ? `${Math.round(n).toLocaleString("ko-KR")}원` : `${(Math.round(n / 1_000) / 10).toLocaleString("ko-KR")}만`);
const line = (pts: [number, number][]) => pts.map(([a, b], i) => `${i ? "L" : "M"}${a.toFixed(1)} ${b.toFixed(1)}`).join(" ");
const dateOr = (v: string | null) => (v ? md(v) : "안 닿음");

// 소진 예측: 이번 달 실제 남은 예산에 빠름·중앙·느림 예상 선을 이어 월말까지 보여 준다
export default function ForecastChart({ forecast: f }: { forecast: Forecast }) {
  const asOf = f.assumptions.asOf;
  const monthStart = `${asOf.slice(0, 8)}01`;
  const series = useLoad(() => getBudgetSeries(monthStart, asOf), [monthStart, asOf]);
  const svgRef = useRef<SVGSVGElement>(null);
  const [hover, setHover] = useState<number | null>(null);

  const today = dayOf(asOf);
  const lastDay = new Date(f.year, f.month, 0).getDate();
  // 오늘의 값은 예측이 시작하는 값과 같게 맞춘다
  const actual = [
    ...(series.data?.find((s) => s.year === f.year && s.month === f.month)?.points ?? [])
      .filter((p) => dayOf(p.date) < today)
      .map((p) => ({ day: dayOf(p.date), value: p.remainingAmount })),
    { day: today, value: f.remainingAmount },
  ];
  const future = [
    { day: today, fast: f.remainingAmount, mid: f.remainingAmount, slow: f.remainingAmount },
    ...f.points.map((p) => ({ day: dayOf(p.date), fast: p.fastRemaining, mid: p.midRemaining, slow: p.slowRemaining })),
  ];

  const max = Math.max(...actual.map((p) => p.value), f.contingencyAmount, 1);
  const x = (d: number) => PAD.l + ((d - 1) / Math.max(lastDay - 1, 1)) * (W - PAD.l - PAD.r);
  const y = (v: number) => PAD.t + (1 - Math.min(Math.max(v, 0), max) / max) * (H - PAD.t - PAD.b);
  const path = (key: "fast" | "mid" | "slow") => future.map((p) => [x(p.day), y(p[key])] as [number, number]);
  const band = `${line(path("fast"))} ${[...path("slow")].reverse().map(([a, b]) => `L${a.toFixed(1)} ${b.toFixed(1)}`).join(" ")} Z`;
  // 오늘과 겹치는 눈금은 뺀다
  const xTicks = [1, 5, 10, 15, 20, 25, lastDay].filter((d, i, a) => d <= lastDay && a.indexOf(d) === i && Math.abs(d - today) > 2);
  const reach = (["fast", "mid"] as const)
    .map((k) => ({ key: k, date: f[k].noticeLineDate, color: k === "fast" ? SCENARIOS[0].color : SCENARIOS[1].color }))
    .filter((r): r is typeof r & { date: string } => r.date != null && r.date >= asOf);

  const onMove = (e: React.PointerEvent<SVGSVGElement>) => {
    const box = svgRef.current?.getBoundingClientRect();
    if (!box) return;
    const px = ((e.clientX - box.left) / box.width) * W;
    const d = Math.round(1 + ((px - PAD.l) / (W - PAD.l - PAD.r)) * (lastDay - 1));
    setHover(Math.min(lastDay, Math.max(1, d)));
  };
  const hoverActual = hover != null && hover <= today ? actual.find((p) => p.day === hover) : undefined;
  const hoverFuture = hover != null && hover > today ? future.find((p) => p.day === hover) : undefined;

  const inputs: [string, string][] = [
    ["최근 7일 하루 평균", won(Math.round(f.assumptions.baseDailyDemand))],
    ["수요 배율", String(f.assumptions.demandMultiplier)],
    ["범위 폭", `±${Math.round(f.assumptions.rangeRatio * 100)}%`],
    ["계절 지수", "미적용"],
    ["명절", "미적용"],
    ["모델", f.assumptions.modelVersion],
  ];

  return (
    <div className="space-y-3">
      {series.error && <p role="alert" className="note">{series.error}</p>}
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
        <span className="flex items-center gap-1.5"><i className="inline-block h-0.5 w-5 bg-slate-800" />실제 남은 예산</span>
        {SCENARIOS.map((s) => (
          <span key={s.key} className="flex items-center gap-1.5">
            <svg width="22" height="8" aria-hidden><line x1="0" y1="4" x2="22" y2="4" stroke={s.color} strokeWidth={s.width} strokeDasharray={s.dash} /></svg>
            {s.label}
          </span>
        ))}
        <span className="flex items-center gap-1.5"><i className="inline-block w-5 border-t-2 border-dashed border-amber-600" />대비금</span>
      </div>

      <svg
        ref={svgRef}
        viewBox={`0 0 ${W} ${H}`}
        className="block w-full touch-none select-none"
        role="img"
        aria-label={`${f.month}월 소진 예측. 오늘 남은 예산 ${won(f.remainingAmount)}, 대비금 도달일(중앙) ${dateOr(f.mid.noticeLineDate)}, 예상 소진일(중앙) ${dateOr(f.mid.exhaustionDate)}`}
        onPointerMove={onMove}
        onPointerLeave={() => setHover(null)}
      >
        {Y_TICKS.map((t) => (
          <g key={t}>
            <line x1={PAD.l} x2={W - PAD.r} y1={y(max * t)} y2={y(max * t)} className="stroke-slate-200" />
            <text x={PAD.l - 8} y={y(max * t) + 4} textAnchor="end" className="fill-slate-500" fontSize={12}>{shortWon(max * t)}</text>
          </g>
        ))}
        {xTicks.map((d) => <text key={d} x={x(d)} y={H - 10} textAnchor="middle" className="fill-slate-500" fontSize={12}>{f.month}.{d}</text>)}
        <text x={x(today)} y={H - 10} textAnchor="middle" className="fill-slate-800" fontSize={12} fontWeight={700}>오늘 {f.month}.{today}</text>

        <path d={band} fill="#2a78d6" fillOpacity={0.12} />
        <line x1={PAD.l} x2={W - PAD.r} y1={y(f.contingencyAmount)} y2={y(f.contingencyAmount)} className="stroke-amber-600" strokeWidth={1.5} strokeDasharray="6 4" />
        <text x={PAD.l + 6} y={y(f.contingencyAmount) - 6} className="fill-amber-800" fontSize={12} fontWeight={700}>대비금 {shortWon(f.contingencyAmount)}</text>
        <line x1={x(today)} x2={x(today)} y1={PAD.t} y2={H - PAD.b} className="stroke-slate-400" strokeDasharray="2 3" />
        <path d={line(actual.map((p) => [x(p.day), y(p.value)]))} fill="none" className="stroke-slate-800" strokeWidth={3} strokeLinejoin="round" strokeLinecap="round" />
        {SCENARIOS.map((s) => <path key={s.key} d={line(path(s.key))} fill="none" stroke={s.color} strokeWidth={s.width} strokeDasharray={s.dash} />)}
        <circle cx={x(today)} cy={y(f.remainingAmount)} r={5} className="fill-white stroke-slate-800" strokeWidth={2.5} />
        {reach.map((r, i) => (
          <g key={r.key}>
            <circle cx={x(dayOf(r.date))} cy={y(f.contingencyAmount)} r={4.5} fill={r.color} className="stroke-white" strokeWidth={2} />
            {/* 두 날짜의 글자가 겹치지 않게 빠를 때는 선 아래, 중앙은 선 위에 적는다 */}
            <text x={Math.min(x(dayOf(r.date)), W - PAD.r - 18)} y={y(f.contingencyAmount) + (i === 0 && r.key === "fast" ? 16 : -8)} textAnchor="middle" className="fill-slate-800" fontSize={11}>{md(r.date)}</text>
          </g>
        ))}

        {hover != null && (hoverActual || hoverFuture) && (
          <g pointerEvents="none">
            <line x1={x(hover)} x2={x(hover)} y1={PAD.t} y2={H - PAD.b} className="stroke-slate-500" />
            <rect x={Math.min(Math.max(x(hover) - 85, PAD.l), W - PAD.r - 170)} y={PAD.t} width={170} height={hoverFuture ? 74 : 42} rx={6} className="fill-slate-900" />
            {(hoverActual
              ? [`${f.month}.${hover} 실제`, won(hoverActual.value)]
              : [`${f.month}.${hover} 예상 남은 예산`, ...SCENARIOS.map((s) => `${s.label} ${won(hoverFuture![s.key])}`)]
            ).map((t, i) => (
              <text key={t} x={Math.min(Math.max(x(hover) - 85, PAD.l), W - PAD.r - 170) + 85} y={PAD.t + 17 + i * 16} textAnchor="middle" className="fill-white" fontSize={12}>{t}</text>
            ))}
          </g>
        )}
      </svg>

      <dl className="grid gap-3 sm:grid-cols-3">
        {([
          ["예상 소진일 (빠름 / 중앙 / 느림)", [f.fast, f.mid, f.slow].map((s) => dateOr(s.exhaustionDate)).join(" / ")],
          ["대비금 도달일 (중앙)", dateOr(f.mid.noticeLineDate)],
          ["최소 보장 인원", `${f.guaranteedUsers.toLocaleString("ko-KR")}명`],
        ] as const).map(([k, v]) => (
          <div key={k} className="rounded-lg bg-slate-50 px-3 py-2"><dt className="note">{k}</dt><dd className="mt-0.5 font-bold tabular-nums">{v}</dd></div>
        ))}
      </dl>
      <dl className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-slate-200 pt-3 text-[13px]" aria-label="예측에 넣은 값">
        <span className="font-bold">예측에 넣은 값</span>
        {inputs.map(([k, v]) => (
          <div key={k} className="flex gap-1"><dt className="text-slate-500">{k}</dt><dd className="font-medium tabular-nums">{v}</dd></div>
        ))}
      </dl>
    </div>
  );
}
