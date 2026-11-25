"use client";

import { useRef, useState } from "react";
import type { BudgetTimePoint, MonthlyBudget } from "@/lib/adminApi";
import { won } from "@/lib/format";

const W = 720;
const H = 320;
const PAD = { l: 62, r: 20, t: 20, b: 40 };
const PRESETS = [5, 10, 15, 20];
const Y_TICKS = [0, 0.25, 0.5, 0.75, 1];
// 가정(서버 값이 아니다): 요율이 기본 요율 대비 1% 오를 때마다 하루 지급이 이 비율(%)만큼 는다고 본다
const ELASTICITY = 0.3;

const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => from + i);
const line = (pts: [number, number][]) => pts.map(([a, b], i) => `${i ? "L" : "M"}${a.toFixed(1)} ${b.toFixed(1)}`).join(" ");
// 1만 원 미만은 원 단위로, 그 이상은 만 단위(소수 첫째 자리까지)로 줄여 쓴다
const shortWon = (n: number) => (n < 10_000 ? `${Math.round(n).toLocaleString("ko-KR")}원` : `${(Math.round(n / 1_000) / 10).toLocaleString("ko-KR")}만`);
const dayOf = (iso: string) => Number(iso.slice(8, 10));

type Props = {
  rate: string;
  onRate: (v: string) => void;
  budget: MonthlyBudget; // 이번 달 월 예산(서버 값)
  points: BudgetTimePoint[]; // 이번 달 일별 남은 예산(서버 값, 오늘까지)
  today: number; // 오늘 날짜(일)
  daysInMonth: number;
  noticeDays: number; // 적립 중단 공지 후 중단까지의 일수(서버 설정)
  emergencyRatio: number; // 긴급 중단선 = 대비금 × 비율(서버 설정)
};

export default function RateSimulator({ rate, onRate, budget, points, today, daysInMonth, noticeDays, emergencyRatio }: Props) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [hover, setHover] = useState<number | null>(null);

  const month = budget.budgetMonth;
  const dayLabel = (d: number) => `${month}.${d}`;
  const budgetTotal = budget.allocatedAmount + budget.carriedInAmount - budget.carriedOutAmount;
  const baseRate = (budget.baseRate ?? 0) * 100;
  const used = budget.paidAmount + budget.reservedAmount;
  const baseDaily = today > 0 ? used / today : 0;

  const x = (d: number) => PAD.l + ((d - 1) / Math.max(daysInMonth - 1, 1)) * (W - PAD.l - PAD.r);
  const y = (v: number) => PAD.t + (1 - Math.min(v, budgetTotal) / Math.max(budgetTotal, 1)) * (H - PAD.t - PAD.b);

  // 그날 끝의 실제 남은 예산. 점이 없는 날은 직전 값을 이어 쓰고, 첫 점 이전은 월 예산 전체다
  const actualRemaining = (d: number) => {
    const prior = points.filter((p) => dayOf(p.date) <= d);
    return prior.length ? prior[prior.length - 1].remainingAmount : budgetTotal;
  };
  const dailyPayout = (r: number) =>
    baseRate > 0 ? Math.max(0, baseDaily * (r / baseRate) * (1 + (ELASTICITY * (r - baseRate)) / baseRate)) : baseDaily;
  const projectedRemaining = (r: number, d: number) => Math.max(0, actualRemaining(today) - dailyPayout(r) * (d - today));
  const remainingAt = (r: number, d: number) => (d <= today ? actualRemaining(d) : projectedRemaining(r, d));
  const firstDayBelow = (r: number, threshold: number): number | null => {
    const now = actualRemaining(today);
    if (now <= threshold) return today;
    const pay = dailyPayout(r);
    if (pay <= 0) return null;
    const day = today + Math.ceil((now - threshold) / pay);
    return day > daysInMonth ? null : day;
  };

  const xTicks = [1, 5, 10, 15, 20, 25, daysInMonth].filter((d, i, a) => d <= daysInMonth && a.indexOf(d) === i);
  const parsed = Number(rate);
  const r = rate.trim() && Number.isFinite(parsed) ? Math.min(Math.max(parsed, 0), 100) : 0;
  const reserve = budget.contingencyAmount;
  const stop = reserve * emergencyRatio;

  const actual = line(range(1, today).map((d) => [x(d), y(actualRemaining(d))]));
  const futureDays = range(today, daysInMonth);
  const projPts = futureDays.map((d) => [x(d), y(projectedRemaining(r, d))] as [number, number]);
  const projected = line(projPts);
  const area = `${projected} L${x(daysInMonth)} ${y(0)} L${x(today)} ${y(0)} Z`;
  const base = line(futureDays.map((d) => [x(d), y(projectedRemaining(baseRate, d))]));
  const changed = Math.abs(r - baseRate) > 0.001;

  const reachReserve = firstDayBelow(r, reserve);
  const reachStop = firstDayBelow(r, stop);
  const reachZero = firstDayBelow(r, 0);
  const endBalance = projectedRemaining(r, daysInMonth);
  const danger = reachReserve !== null && reachReserve - today < 3;

  const onMove = (e: React.PointerEvent<SVGSVGElement>) => {
    const box = svgRef.current?.getBoundingClientRect();
    if (!box) return;
    const px = ((e.clientX - box.left) / box.width) * W;
    const d = Math.round(1 + ((px - PAD.l) / (W - PAD.l - PAD.r)) * (daysInMonth - 1));
    setHover(Math.min(daysInMonth, Math.max(1, d)));
  };

  const hv = hover !== null ? remainingAt(r, hover) : 0;
  const tipX = hover !== null ? Math.min(Math.max(x(hover), PAD.l + 80), W - PAD.r - 80) : 0;

  return (
    <div className="space-y-4">
      <div>
        <div className="flex flex-wrap items-center gap-3">
          <label htmlFor="rate-range" className="label !mb-0 w-20 shrink-0">캐시백 요율</label>
          <input
            id="rate-range"
            type="range"
            min={0}
            max={30}
            step={0.5}
            value={Math.min(r, 30)}
            onChange={(e) => onRate(e.target.value)}
            className="h-2 min-w-40 flex-1 cursor-pointer accent-brand-600"
            aria-label="캐시백 요율 슬라이더"
          />
          <div className="flex items-center gap-1">
            <input
              inputMode="decimal"
              className="field !w-20 text-right text-lg font-bold tabular-nums"
              value={rate}
              onChange={(e) => onRate(e.target.value.replace(/[^\d.]/g, ""))}
              aria-label="캐시백 요율 직접 입력"
            />
            <span className="font-bold">%</span>
          </div>
        </div>
        <div className="mt-2 flex flex-wrap gap-2">
          {PRESETS.map((p) => (
            <button key={p} type="button" onClick={() => onRate(String(p))} className={`chip ${r === p ? "chip-on" : ""}`}>
              {p}%{p === baseRate ? " (현재)" : ""}
            </button>
          ))}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-2">
        <svg
          ref={svgRef}
          viewBox={`0 0 ${W} ${H}`}
          className="block w-full touch-none select-none"
          role="img"
          aria-label={`요율 ${r}% 적용 시 ${month}월 남은 예산 예상 그래프. ${reachReserve ? `${dayLabel(reachReserve)}에 대비금에 닿을 것으로 예상됩니다.` : "월말까지 대비금에 닿지 않습니다."}`}
          onPointerMove={onMove}
          onPointerLeave={() => setHover(null)}
        >
          {Y_TICKS.map((t) => (
            <g key={t}>
              <line x1={PAD.l} x2={W - PAD.r} y1={y(budgetTotal * t)} y2={y(budgetTotal * t)} className="stroke-slate-200" strokeWidth={1} />
              <text x={PAD.l - 8} y={y(budgetTotal * t) + 4} textAnchor="end" className="fill-slate-500" fontSize={12}>{shortWon(budgetTotal * t)}</text>
            </g>
          ))}
          {xTicks.map((d) => (
            <text key={d} x={x(d)} y={H - 14} textAnchor="middle" className="fill-slate-500" fontSize={12}>{dayLabel(d)}</text>
          ))}

          <line x1={PAD.l} x2={W - PAD.r} y1={y(reserve)} y2={y(reserve)} className="stroke-amber-600" strokeWidth={1.5} strokeDasharray="6 4" />
          <text x={W - PAD.r - 4} y={y(reserve) - 6} textAnchor="end" className="fill-amber-800" fontSize={12} fontWeight={700}>대비금 {shortWon(reserve)}</text>
          <line x1={PAD.l} x2={W - PAD.r} y1={y(stop)} y2={y(stop)} className="stroke-red-600" strokeWidth={1.5} strokeDasharray="6 4" />
          <text x={W - PAD.r - 4} y={y(stop) + 15} textAnchor="end" className="fill-red-700" fontSize={12} fontWeight={700}>긴급 중단 {shortWon(stop)}</text>

          <line x1={x(today)} x2={x(today)} y1={PAD.t} y2={H - PAD.b} className="stroke-slate-400" strokeWidth={1} strokeDasharray="2 3" />
          <text x={x(today) - 6} y={PAD.t + 10} textAnchor="end" className="fill-slate-600" fontSize={12}>오늘 {dayLabel(today)}</text>

          <path d={area} className="fill-brand-600" fillOpacity={0.12} style={{ transition: "d 200ms ease-out" }} />
          {changed && <path d={base} fill="none" className="stroke-slate-400" strokeWidth={2} strokeDasharray="5 4" />}
          <path d={actual} fill="none" className="stroke-slate-700" strokeWidth={3} strokeLinecap="round" />
          <path d={projected} fill="none" className="stroke-brand-600" strokeWidth={3.5} strokeLinecap="round" strokeLinejoin="round" style={{ transition: "d 200ms ease-out" }} />
          <circle cx={x(today)} cy={y(actualRemaining(today))} r={5} className="fill-white stroke-slate-700" strokeWidth={2.5} />

          {hover !== null && (
            <g pointerEvents="none">
              <line x1={x(hover)} x2={x(hover)} y1={PAD.t} y2={H - PAD.b} className="stroke-slate-500" strokeWidth={1} />
              <circle cx={x(hover)} cy={y(hv)} r={5} className="fill-brand-600 stroke-white" strokeWidth={2} />
              <rect x={tipX - 78} y={PAD.t + 16} width={156} height={40} rx={6} className="fill-slate-900" />
              <text x={tipX} y={PAD.t + 33} textAnchor="middle" className="fill-white" fontSize={12}>{dayLabel(hover)} {hover <= today ? "실제" : "예상"} 남은 예산</text>
              <text x={tipX} y={PAD.t + 49} textAnchor="middle" className="fill-white" fontSize={13} fontWeight={700}>{won(Math.round(hv))}</text>
            </g>
          )}
        </svg>
        <div className="flex flex-wrap gap-x-4 gap-y-1 px-2 pb-1 pt-0.5 text-xs text-slate-600">
          <span className="flex items-center gap-1.5"><i className="inline-block h-0.5 w-5 bg-slate-700" />실제 남은 예산</span>
          <span className="flex items-center gap-1.5"><i className="inline-block h-1 w-5 rounded bg-brand-600" />요율 {r}% 예상</span>
          {changed && <span className="flex items-center gap-1.5"><i className="inline-block h-0.5 w-5 border-t-2 border-dashed border-slate-400" />현재 요율 {baseRate}% 예상</span>}
        </div>
      </div>

      <dl className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-lg bg-slate-50 px-3 py-2.5">
          <dt className="note">하루 예상 지급액</dt>
          <dd className="mt-0.5 text-lg font-bold tabular-nums">{won(Math.round(dailyPayout(r)))}</dd>
        </div>
        <div className={`rounded-lg px-3 py-2.5 ${danger ? "bg-red-50" : reachReserve ? "bg-amber-50" : "bg-emerald-50"}`}>
          <dt className="note">대비금 도달 예상</dt>
          <dd className={`mt-0.5 text-lg font-bold ${danger ? "text-red-700" : reachReserve ? "text-amber-800" : "text-emerald-700"}`}>
            {reachReserve ? dayLabel(reachReserve) : "이번 달 안 닿음"}
          </dd>
        </div>
        <div className="rounded-lg bg-slate-50 px-3 py-2.5">
          <dt className="note">월말 예상 남은 예산</dt>
          <dd className="mt-0.5 text-lg font-bold tabular-nums">{won(Math.round(endBalance))}</dd>
        </div>
      </dl>

      {danger && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">
          {dayLabel(reachReserve!)}에 대비금에 닿아 {noticeDays}일 안에 적립 중단 공지가 필요해요. 요율을 낮추는 것을 검토해 주세요.
        </p>
      )}
      <p className="note">
        {reachStop && `긴급 중단선 도달 ${dayLabel(reachStop)}`}{reachStop && reachZero ? " · " : ""}{reachZero && `소진 ${dayLabel(reachZero)}`}
        {(reachStop || reachZero) && " · "}
        {used === 0
          ? "아직 이번 달 결제가 없어서 하루 지급액을 0원으로 보고 있어요."
          : `가정: 이번 달 지금까지의 하루 평균 지급·확보액(${won(Math.round(baseDaily))}, 기본 요율 ${baseRate}%)을 기준으로, 요율이 기본 요율 대비 1% 오를 때마다 수요가 ${ELASTICITY}%씩 늘어난다고 보았습니다.`}
        {" "}저장 전 미리보기이며, 하루 평균과 남은 예산은 실제 값이고 수요 변화는 가정입니다.
      </p>
    </div>
  );
}
