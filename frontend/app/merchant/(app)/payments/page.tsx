"use client";

import { useState } from "react";
import CancelPaymentForm from "@/components/CancelPaymentForm";
import Header from "@/components/Header";
import MerchantTabs from "@/components/MerchantTabs";
import { ApiError } from "@/lib/api";
import { addDays, ymd } from "@/lib/dates";
import { cancelPayment, getMerchantPayments } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { paymentErrorText } from "@/lib/paymentErrors";
import { useLoad } from "@/lib/useLoad";
import type { MerchantPaymentItem } from "@/lib/types";

// 서버의 조회 기간은 양 끝 포함, 최대 31일이다
const RANGES = [
  { label: "오늘", back: 0, days: 1 },
  { label: "어제", back: 1, days: 1 },
  { label: "최근 7일", back: 0, days: 7 },
  { label: "최근 30일", back: 0, days: 30 },
] as const;

const PAGE_SIZE = 10;
const fmt = (iso?: string | null) => (iso ? iso.slice(5, 16).replace("T", " ") : "-");
const payNo = (id: number) => `PAY-${String(id).padStart(4, "0")}`;

type Notice = { kind: "ok" | "error"; message: string };

// 점주의 결제 내역과 매출. 목록에서 결제를 골라 바로 전액 취소할 수 있다 (가맹점은 토큰의 회원으로 찾는다)
export default function MerchantPaymentsPage() {
  const [range, setRange] = useState(0);
  const [page, setPage] = useState(0);
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);

  const r = RANGES[range];
  const today = new Date();
  const to = ymd(addDays(today, -r.back));
  const from = ymd(addDays(today, -r.back - (r.days - 1)));
  const load = useLoad(() => getMerchantPayments(from, to, page, PAGE_SIZE), [from, to, page]);
  const data = load.data;

  const pick = (i: number) => {
    setRange(i);
    setPage(0);
    setConfirmId(null);
    setNotice(null);
  };

  const cancel = async (id: number) => {
    setBusyId(id);
    setNotice(null);
    try {
      const res = await cancelPayment(id);
      setNotice({ kind: "ok", message: `${payNo(id)} ${won(res.amount)} 결제를 취소했어요. 데브머니 ${won(res.walletUsedAmount)}, 캐시백 ${won(res.cashbackUsedAmount)}이 돌아갔어요` });
      setConfirmId(null);
      load.reload();
    } catch (e) {
      setNotice({ kind: "error", message: e instanceof ApiError ? paymentErrorText(e.code, e.message) : paymentErrorText() });
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <Header title="매출 · 정산" />
      <MerchantTabs />
      <div className="space-y-3 px-5 pb-5">
        <div className="flex flex-wrap gap-2" role="group" aria-label="조회 기간">
          {RANGES.map((x, i) => (
            <button key={x.label} type="button" onClick={() => pick(i)} aria-pressed={range === i} className={`chip ${range === i ? "chip-on" : ""}`}>
              {x.label}
            </button>
          ))}
        </div>

        <section className="card">
          <div className="note">순매출 ({from === to ? from : `${from} ~ ${to}`})</div>
          {data ? (
            <>
              <div className={`text-2xl font-bold tabular-nums ${data.netSales < 0 ? "amt-out" : "amt-in"}`}>{won(data.netSales)}</div>
              <dl className="mt-3 space-y-1.5 text-sm">
                <div className="flex justify-between"><dt>결제 {data.paymentCount}건</dt><dd className="font-semibold tabular-nums">{won(data.paymentAmount)}</dd></div>
                <div className="flex justify-between"><dt>취소 {data.cancelCount}건</dt><dd className="font-semibold tabular-nums amt-out">- {won(data.cancelAmount)}</dd></div>
              </dl>
              <p className="note mt-2">결제는 완료한 날, 취소는 취소한 날에 세요. 일 정산과 같은 기준이에요.</p>
            </>
          ) : load.error ? (
            <p role="alert" className="mt-1 text-[13px] font-medium text-red-700">{load.error}</p>
          ) : (
            <p role="status" className="note mt-1">불러오는 중이에요…</p>
          )}
        </section>

        {notice && (
          <p role={notice.kind === "error" ? "alert" : "status"} className={`rounded-lg px-3 py-2 text-[13px] font-medium ${notice.kind === "error" ? "bg-red-50 text-red-700" : "bg-emerald-50 text-emerald-900"}`}>
            {notice.message}
          </p>
        )}

        <h2 className="px-1.5 pt-2 text-base font-medium">결제 내역</h2>
        {data && data.payments.content.length === 0 && (
          <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">이 기간에는 결제 내역이 없어요.</p>
        )}
        {data && data.payments.content.length > 0 && (
          <ul>
            {data.payments.content.map((p) => (
              <PaymentRow
                key={p.paymentId}
                p={p}
                asking={confirmId === p.paymentId}
                busy={busyId === p.paymentId}
                onAsk={() => { setConfirmId(p.paymentId); setNotice(null); }}
                onBack={() => setConfirmId(null)}
                onCancel={() => cancel(p.paymentId)}
              />
            ))}
          </ul>
        )}
        {data && data.payments.totalPages > 1 && (
          <div className="flex items-center justify-between pt-1">
            <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">이전</button>
            <span className="note">{page + 1} / {data.payments.totalPages}</span>
            <button type="button" disabled={page + 1 >= data.payments.totalPages} onClick={() => setPage(page + 1)} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">다음</button>
          </div>
        )}

        <details className="pt-2">
          <summary className="cursor-pointer px-1.5 text-[13px] text-zinc-600">영수증의 결제번호로 직접 취소하기</summary>
          <div className="pt-2"><CancelPaymentForm /></div>
        </details>
      </div>
    </>
  );
}

function PaymentRow({ p, asking, busy, onAsk, onBack, onCancel }: {
  p: MerchantPaymentItem; asking: boolean; busy: boolean; onAsk: () => void; onBack: () => void; onCancel: () => void;
}) {
  const canceled = p.status === "CANCELED";
  return (
    <li className="border-t border-zinc-200 px-1.5 py-2.5">
      <div className="flex items-center justify-between">
        <div>
          <div className="font-medium">{payNo(p.paymentId)}</div>
          <div className="note">{fmt(p.completedAt)} 결제{canceled && ` · ${fmt(p.canceledAt)} 취소`}</div>
          <div className="note">데브머니 {won(p.walletUsedAmount)} · 캐시백 {won(p.cashbackUsedAmount)}</div>
        </div>
        <div className="text-right">
          <div className={`font-semibold tabular-nums ${canceled ? "text-zinc-400 line-through" : ""}`}>{won(p.amount)}</div>
          {canceled ? (
            <span className="badge badge-bad">취소됨</span>
          ) : (
            <button type="button" onClick={onAsk} disabled={asking || busy} className="chip mt-1 !text-red-700 disabled:opacity-40">취소</button>
          )}
        </div>
      </div>
      {asking && !canceled && (
        <div role="alertdialog" aria-label="결제 취소 확인" className="mt-2 flex flex-wrap items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2">
          <span className="text-[13px] font-medium text-red-700">{payNo(p.paymentId)} {won(p.amount)} 결제를 전액 취소할까요?</span>
          <button type="button" onClick={onBack} disabled={busy} className="btn btn-outline btn-sm !w-auto">돌아가기</button>
          <button type="button" onClick={onCancel} disabled={busy} className="btn btn-sm !w-auto !bg-red-600 !text-white hover:!bg-red-700 disabled:opacity-40">{busy ? "취소 중…" : "취소하기"}</button>
        </div>
      )}
    </li>
  );
}
