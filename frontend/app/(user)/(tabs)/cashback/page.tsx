"use client";

import Link from "next/link";
import Header from "@/components/Header";
import Progress from "@/components/Progress";
import { won } from "@/lib/format";
import { loadCtx } from "@/lib/devContext";
import {
  EXCLUSION_TEXT, STATUS_BADGE, STATUS_LABEL, getCashbackSummary, getEarnings, getMonthlyLimit, getNotices, mmdd, pctText,
} from "@/lib/cashbackApi";
import { useLoad } from "@/lib/useLoad";

export default function CashbackPage() {
  const walletId = loadCtx().walletId;
  const summary = useLoad(() => getCashbackSummary(walletId), [walletId]);
  const limit = useLoad(() => getMonthlyLimit(walletId), [walletId]);
  const recent = useLoad(() => getEarnings(walletId, undefined, 0, 3), [walletId]);
  const notices = useLoad(() => getNotices(undefined, 0, 1), []);

  const s = summary.data;
  const l = limit.data;
  const notice = notices.data?.content[0];
  const error = summary.error ?? limit.error ?? recent.error;

  return (
    <>
      <Header title="내 캐시백" />
      <div className="space-y-3 px-5 pb-4">
        {notice && (
          <div className="rounded-xl border border-amber-500 bg-amber-50 px-4 py-3 text-[13px] text-amber-900">
            <span className="font-semibold">공지</span> · {notice.content}
            {notice.rate != null && notice.previousRate != null && ` (요율 ${pctText(notice.previousRate)} → ${pctText(notice.rate)})`}
          </div>
        )}
        {error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-[13px] text-red-700">{error}</p>}
        <section className="card">
          <div className="note">{s ? `${s.month}월 캐시백` : "이번 달 캐시백"}</div>
          <div className="mt-1 text-[28px] font-bold text-emerald-600">{s ? won(s.earnedAmount) : "–"}</div>
          {l && l.limit > 0 && (
            <>
              <div className="mt-3"><Progress value={l.usedAmount} max={l.limit} /></div>
              <p className="note mt-2">월 적립 한도 {won(l.limit)} · 월 적립 한도 잔여 {won(l.remaining)}</p>
            </>
          )}
          {s && <p className="mt-1 text-[13px]">적립 예정 {won(s.pendingAmount)} · 지급 완료 {won(s.paidAmount)}</p>}
          <Link href="/cashback/accruals" className="btn btn-outline btn-sm mt-3">적립 내역 보기 →</Link>
        </section>
        <h2 className="px-1.5 pt-2 text-base font-medium">최근 적립</h2>
        {recent.data && recent.data.content.length === 0 && <p className="note px-1.5">아직 적립 내역이 없어요.</p>}
        <ul>
          {recent.data?.content.map((a) => (
            <li key={a.earningId} className="flex items-center justify-between border-t border-zinc-200 px-1.5 py-2.5">
              <div>
                <div className="font-medium">결제 #{a.paymentId} <span className={a.status === "EXCLUDED" ? "text-slate-400" : "amt-cb"}>+{a.amount}원</span></div>
                <div className="note">
                  {a.status === "EXCLUDED"
                    ? `제외 사유: ${EXCLUSION_TEXT[a.exclusionReason ?? "NONE"]}`
                    : a.status === "PAID" ? `지급 완료 ${mmdd(a.paidAt)}` : a.status === "PENDING" ? `지급 예정 ${mmdd(a.payableAt)}` : mmdd(a.requestedAt)}
                </div>
              </div>
              <span className={`badge ${STATUS_BADGE[a.status]}`}>{STATUS_LABEL[a.status]}</span>
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
