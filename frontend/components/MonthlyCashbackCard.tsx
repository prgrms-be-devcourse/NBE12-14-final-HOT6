"use client";

import Progress from "@/components/Progress";
import { getMonthlyLimit, type CashbackSummary } from "@/lib/cashbackApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

// 하단 캐시백 탭 맨 위의 이번 달 적립액과 월 적립 한도
export default function MonthlyCashbackCard({ summary: s }: { summary: CashbackSummary | null }) {
  const limit = useLoad(getMonthlyLimit, []);
  const l = limit.data;
  return (
    <section className="card mb-3">
      <div className="note">{s ? `${s.month}월 캐시백` : "이번 달 캐시백"}</div>
      <div className="mt-1 text-[28px] font-bold text-emerald-600">{s ? won(s.earnedAmount) : "–"}</div>
      {l && l.limit > 0 && (
        <>
          <div className="mt-3"><Progress value={l.usedAmount} max={l.limit} /></div>
          <p className="note mt-2">월 적립 한도 {won(l.limit)} · 월 적립 한도 잔여 {won(l.remaining)}</p>
        </>
      )}
      {limit.error && <p role="alert" className="mt-2 text-[13px] text-red-600">{limit.error}</p>}
    </section>
  );
}
