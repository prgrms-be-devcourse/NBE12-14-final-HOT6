"use client";

import { useRef, useState } from "react";
import { getDailyEarnings, getMonthlyLimitUsage, md } from "@/lib/adminApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

const errorBox = "rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600";

// 이번 달에 적립한 이용자가 월 한도를 얼마나 썼는지
function LimitUsage() {
  const load = useLoad(() => getMonthlyLimitUsage(), []);
  const d = load.data;
  const rows = d
    ? ([
        ["25% 미만", d.below25],
        ["25% 이상 50% 미만", d.below50],
        ["50% 이상 75% 미만", d.below75],
        ["75% 이상 100% 미만", d.below100],
        ["한도 도달", d.reached],
      ] as const)
    : [];
  const max = Math.max(1, ...rows.map(([, n]) => n));

  return (
    <section className="card space-y-3 !p-5" aria-label="월 한도 사용 분포">
      <h2 className="text-lg font-bold">
        월 한도 사용 분포{" "}
        {d && <span className="note font-normal">{d.month}월 · 적립한 이용자 {d.userCount.toLocaleString("ko-KR")}명 · 지금 한도 {won(d.limit)}</span>}
      </h2>
      {load.error && <p role="alert" className={errorBox}>{load.error}</p>}
      {!d && load.loading && <p role="status" className="note">불러오는 중…</p>}
      {d && d.userCount === 0 && <p className="note">이번 달에 적립한 이용자가 아직 없어요.</p>}
      {d && d.userCount > 0 && (
        <ul className="space-y-2">
          {rows.map(([label, n], i) => (
            <li key={label} className="flex items-center gap-3 text-sm">
              <span className="w-36 shrink-0 text-slate-600">{label}</span>
              <span className="h-4 flex-1 overflow-hidden rounded bg-slate-100">
                <span className={`block h-full rounded ${i === rows.length - 1 ? "bg-amber-500" : "bg-brand-600"}`} style={{ width: `${(n / max) * 100}%` }} />
              </span>
              <span className="w-28 shrink-0 text-right tabular-nums">{n.toLocaleString("ko-KR")}명 ({Math.round((n / d.userCount) * 100)}%)</span>
            </li>
          ))}
        </ul>
      )}
      <p className="note">지급 예정을 포함한 사용량이에요. 한도를 바꾸면 바뀐 한도로 다시 나눠요.</p>
    </section>
  );
}

const W = 720;
const H = 260;
const PAD = { l: 62, r: 20, t: 22, b: 30 };
const percent = (rate: number) => `${Math.round(rate * 10_000) / 100}%`;

// 최근 30일의 하루 신규 적립액, 직전 7일 평균, 요율을 바꾼 날
function DailyEarnings() {
  const load = useLoad(() => getDailyEarnings(), []);
  const svgRef = useRef<SVGSVGElement>(null);
  const [hover, setHover] = useState<number | null>(null);
  const days = load.data?.days ?? [];
  const max = Math.max(1, ...days.map((d) => Math.max(d.amount, d.previousAverage)));
  const slot = (W - PAD.l - PAD.r) / Math.max(days.length, 1);
  const x = (i: number) => PAD.l + slot * (i + 0.5);
  const y = (v: number) => PAD.t + (1 - v / max) * (H - PAD.t - PAD.b);
  // 앞날과 요율이 다른 날
  const changes = days.flatMap((d, i) => {
    const before = days[i - 1]?.rate;
    return before != null && d.rate != null && before !== d.rate ? [{ i, date: d.date, from: before, to: d.rate }] : [];
  });
  const picked = hover != null ? days[hover] : undefined;
  const boxX = hover != null ? Math.min(Math.max(x(hover) - 85, PAD.l), W - PAD.r - 170) : 0;

  const onMove = (e: React.PointerEvent<SVGSVGElement>) => {
    const box = svgRef.current?.getBoundingClientRect();
    if (!box) return;
    const px = ((e.clientX - box.left) / box.width) * W;
    setHover(Math.min(days.length - 1, Math.max(0, Math.floor((px - PAD.l) / slot))));
  };

  return (
    <section className="card space-y-3 !p-5" aria-label="일별 적립액">
      <h2 className="text-lg font-bold">일별 적립액 <span className="note font-normal">최근 30일</span></h2>
      {load.error && <p role="alert" className={errorBox}>{load.error}</p>}
      {!load.data && load.loading && <p role="status" className="note">불러오는 중…</p>}
      {load.data && days.every((d) => d.amount === 0) && <p className="note">최근 30일에 적립한 내역이 없어요.</p>}
      {days.some((d) => d.amount > 0) && (
        <>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
            <span className="flex items-center gap-1.5"><i className="inline-block h-2.5 w-2.5 rounded-sm bg-brand-600" />하루 적립액</span>
            <span className="flex items-center gap-1.5"><i className="inline-block h-0.5 w-5 bg-slate-800" />직전 7일 평균</span>
            <span className="flex items-center gap-1.5"><i className="inline-block h-3 border-l-2 border-dashed border-amber-600" />요율 변경</span>
          </div>
          <svg
            ref={svgRef}
            viewBox={`0 0 ${W} ${H}`}
            className="block w-full touch-none select-none"
            role="img"
            aria-label={`최근 30일 일별 적립액: ${days.filter((d) => d.amount > 0).map((d) => `${md(d.date)} ${won(d.amount)}`).join(", ")}`}
            onPointerMove={onMove}
            onPointerLeave={() => setHover(null)}
          >
            {[0, 0.5, 1].map((t) => (
              <g key={t}>
                <line x1={PAD.l} x2={W - PAD.r} y1={y(max * t)} y2={y(max * t)} className="stroke-slate-200" />
                <text x={PAD.l - 8} y={y(max * t) + 4} textAnchor="end" className="fill-slate-500" fontSize={12}>{Math.round(max * t).toLocaleString("ko-KR")}원</text>
              </g>
            ))}
            {days.map((d, i) => (
              <rect key={d.date} x={x(i) - slot * 0.35} y={y(d.amount)} width={slot * 0.7} height={y(0) - y(d.amount)} rx={2} className={hover === i ? "fill-brand-700" : "fill-brand-600"} fillOpacity={i === days.length - 1 ? 0.5 : 1} />
            ))}
            <polyline points={days.map((d, i) => `${x(i).toFixed(1)},${y(d.previousAverage).toFixed(1)}`).join(" ")} fill="none" className="stroke-slate-800" strokeWidth={2} strokeLinejoin="round" />
            {changes.map((c) => (
              <g key={c.date}>
                <line x1={x(c.i) - slot / 2} x2={x(c.i) - slot / 2} y1={PAD.t} y2={H - PAD.b} className="stroke-amber-600" strokeWidth={1.5} strokeDasharray="5 4" />
                <text x={Math.min(Math.max(x(c.i) - slot / 2, PAD.l + 30), W - PAD.r - 30)} y={PAD.t - 8} textAnchor="middle" className="fill-amber-800" fontSize={11} fontWeight={700}>{percent(c.from)} → {percent(c.to)}</text>
              </g>
            ))}
            {days.map((d, i) => (days.length - 1 - i) % 5 === 0 && <text key={d.date} x={x(i)} y={H - 10} textAnchor="middle" className="fill-slate-500" fontSize={12}>{md(d.date)}</text>)}
            {picked && (
              <g pointerEvents="none">
                <rect x={boxX} y={PAD.t} width={170} height={74} rx={6} className="fill-slate-900" />
                {[
                  `${md(picked.date)}${hover === days.length - 1 ? " (오늘, 진행 중)" : ""}`,
                  `적립 ${won(picked.amount)}`,
                  `직전 7일 평균 ${won(picked.previousAverage)}`,
                  `요율 ${picked.rate == null ? "없음" : percent(picked.rate)}`,
                ].map((t, i) => <text key={t} x={boxX + 85} y={PAD.t + 17 + i * 16} textAnchor="middle" className="fill-white" fontSize={12}>{t}</text>)}
              </g>
            )}
          </svg>
        </>
      )}
      <p className="note">접수한 날로 세고, 취소된 건은 빠져요. 오늘의 직전 7일 평균은 소진 예측에 넣은 값과 같아요.</p>
    </section>
  );
}

// 적립 통계: 월 한도 사용 분포와 일별 적립액
export default function EarningStats() {
  return (
    <div className="space-y-5">
      <LimitUsage />
      <DailyEarnings />
    </div>
  );
}
