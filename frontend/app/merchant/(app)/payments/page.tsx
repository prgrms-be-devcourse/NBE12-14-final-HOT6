"use client";

import { useEffect, useState } from "react";
import Header from "@/components/Header";
import { ApiError } from "@/lib/api";
import { ymd } from "@/lib/dates";
import { cancelPayment, getMerchantPayment, getMerchantPayments } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { paymentErrorText } from "@/lib/paymentErrors";
import { useLoad } from "@/lib/useLoad";
import type { MerchantPaymentItem } from "@/lib/types";

const TABS = [
  { value: "today", label: "당일결제내역" },
  { value: "past", label: "과거 결제 취소" },
] as const;
type Tab = (typeof TABS)[number]["value"];

const STATUSES = [
  { value: "ALL", label: "전체" },
  { value: "COMPLETED", label: "완료" },
  { value: "CANCELED", label: "취소" },
] as const;
type StatusFilter = (typeof STATUSES)[number]["value"];

const PAGE_SIZE = 5;
const fmtShort = (iso?: string | null) => (iso ? `${iso.slice(5, 7)}.${iso.slice(8, 10)} ${iso.slice(11, 16)}` : "-");
const fmtFull = (iso?: string | null) => (iso ? iso.slice(0, 16).replace("T", " ") : "-");
const payNo = (id: number) => `PAY-${String(id).padStart(4, "0")}`;

type Notice = { kind: "ok" | "error"; message: string };

// 점주의 결제 내역. 당일결제내역은 목록에서 영수증 창을 열어 취소하고, 지난 결제는 결제번호로 조회해 아래에 나온 영수증에서 전액 취소한다 (가맹점은 토큰의 회원으로 찾는다)
// 지난 날의 매출은 정산에서 본다
export default function MerchantPaymentsPage() {
  const [tab, setTab] = useState<Tab>("today");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [lookupInput, setLookupInput] = useState("");
  const [lookup, setLookup] = useState<MerchantPaymentItem | null>(null);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [lookupError, setLookupError] = useState("");
  const [asking, setAsking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<Notice | null>(null);

  const today = ymd(new Date());
  const load = useLoad(() => getMerchantPayments(today, today, page, PAGE_SIZE), [today, page]);
  const data = load.data;

  const all = data?.payments.content ?? [];
  const items = status === "ALL" ? all : all.filter((p) => (status === "CANCELED") === (p.status === "CANCELED"));
  const selected = tab === "today" ? (all.find((p) => p.paymentId === selectedId) ?? null) : null;

  const open = (id: number) => {
    setSelectedId(id);
    setAsking(false);
    setNotice(null);
  };
  const close = () => {
    setSelectedId(null);
    setLookup(null);
    setAsking(false);
    setNotice(null);
  };
  const pickTab = (value: Tab) => {
    setTab(value);
    setLookupInput("");
    setLookupError("");
    close();
  };

  const search = async (e: React.FormEvent) => {
    e.preventDefault();
    const id = Number(lookupInput.replace(/\D/g, ""));
    if (!Number.isInteger(id) || id <= 0) {
      setLookupError("결제번호를 입력해 주세요");
      return;
    }
    setLookupBusy(true);
    setLookupError("");
    setLookup(null);
    setAsking(false);
    setNotice(null);
    try {
      setLookup(await getMerchantPayment(id));
    } catch (err) {
      setLookupError(err instanceof ApiError ? paymentErrorText(err.code, err.message) : paymentErrorText());
    } finally {
      setLookupBusy(false);
    }
  };

  const cancel = async (id: number) => {
    setBusy(true);
    setNotice(null);
    try {
      const res = await cancelPayment(id);
      setNotice({ kind: "ok", message: `${payNo(id)} ${won(res.amount)} 결제를 취소했어요. 데브머니 ${won(res.walletUsedAmount)}, 캐시백 ${won(res.cashbackUsedAmount)}이 돌아갔어요` });
      setAsking(false);
      load.reload();
      if (lookup) {
        try {
          setLookup(await getMerchantPayment(id));
        } catch {
          /* 취소는 이미 처리됐다. 영수증만 다시 읽지 못했다 */
        }
      }
    } catch (e) {
      setNotice({ kind: "error", message: e instanceof ApiError ? paymentErrorText(e.code, e.message) : paymentErrorText() });
    } finally {
      setBusy(false);
    }
  };

  const receiptProps = (p: MerchantPaymentItem) => ({
    p,
    asking,
    busy,
    notice,
    onAsk: () => { setAsking(true); setNotice(null); },
    onBack: () => setAsking(false),
    onCancel: () => cancel(p.paymentId),
    onClose: close,
  });

  return (
    <>
      <Header title="결제 내역" />
      <div className="space-y-3 px-5 pb-5">
        <div className="mb-1 flex gap-2" role="tablist" aria-label="결제 내역 구분">
          {TABS.map((t) => (
            <button key={t.value} type="button" role="tab" aria-selected={tab === t.value} onClick={() => pickTab(t.value)} className={`chip flex-1 text-center ${tab === t.value ? "chip-on" : ""}`}>
              {t.label}
            </button>
          ))}
        </div>

        {tab === "past" && (
          <>
            <section className="card">
              <h2 className="font-bold">결제번호로 영수증 조회</h2>
              <p className="note mt-1">영수증의 결제번호(예: PAY-0034)를 입력하면 영수증을 확인하고 취소할 수 있어요.</p>
              <form onSubmit={search} className="mt-3 flex gap-2">
                <input className="field tabular-nums" value={lookupInput} onChange={(e) => { setLookupInput(e.target.value); setLookupError(""); }} placeholder="예: PAY-0034 또는 34" aria-label="결제번호" autoComplete="off" />
                <button type="submit" disabled={!lookupInput.trim() || lookupBusy} className="btn btn-primary btn-sm !w-auto shrink-0 disabled:opacity-40">{lookupBusy ? "조회 중…" : "조회"}</button>
              </form>
              {lookupError && <p role="alert" className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{lookupError}</p>}
            </section>

            {lookup && <ReceiptCard {...receiptProps(lookup)} />}
          </>
        )}

        {tab === "today" && (
          <>
            <section className="card">
              <div className="note">당일 순매출 ({today})</div>
              {data ? (
                <>
                  <div className={`text-2xl font-bold tabular-nums ${data.netSales < 0 ? "amt-out" : "amt-in"}`}>{won(data.netSales)}</div>
                  <dl className="mt-3 space-y-1.5 text-sm">
                    <div className="flex justify-between"><dt>결제 {data.paymentCount}건</dt><dd className="font-semibold tabular-nums">{won(data.paymentAmount)}</dd></div>
                    <div className="flex justify-between"><dt>취소 {data.cancelCount}건</dt><dd className="font-semibold tabular-nums amt-out">- {won(data.cancelAmount)}</dd></div>
                  </dl>
                  <p className="note mt-2">결제는 완료한 날, 취소는 취소한 날에 세요. 지난 매출은 정산에서 볼 수 있어요.</p>
                </>
              ) : load.error ? (
                <p role="alert" className="mt-1 text-[13px] font-medium text-red-700">{load.error}</p>
              ) : (
                <p role="status" className="note mt-1">불러오는 중이에요…</p>
              )}
            </section>

            {notice && !selected && (
              <p role={notice.kind === "error" ? "alert" : "status"} className={`rounded-lg px-3 py-2 text-[13px] font-medium ${notice.kind === "error" ? "bg-red-50 text-red-700" : "bg-emerald-50 text-emerald-900"}`}>
                {notice.message}
              </p>
            )}

            <h2 className="px-1.5 pt-2 text-base font-medium">당일결제내역</h2>
            <div className="flex flex-wrap gap-2" role="group" aria-label="결제 상태">
              {STATUSES.map((s) => (
                <button key={s.value} type="button" onClick={() => setStatus(s.value)} aria-pressed={status === s.value} className={`chip ${status === s.value ? "chip-on" : ""}`}>
                  {s.label}
                </button>
              ))}
            </div>
            <p className="note px-1.5">결제를 누르면 영수증이 열리고, 완료된 결제는 영수증에서 취소할 수 있어요.</p>

            {data && items.length === 0 && (
              <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
                {all.length === 0 ? "당일결제내역이 없어요." : "이 상태의 결제 내역이 없어요."}
              </p>
            )}
            {items.length > 0 && (
              <ul className="max-h-[55vh] space-y-2.5 overflow-y-auto overscroll-contain p-0.5">
                {items.map((p) => {
                  const canceled = p.status === "CANCELED";
                  return (
                    <li key={p.paymentId}>
                      <button type="button" onClick={() => open(p.paymentId)} className="flex w-full items-center justify-between gap-3 rounded-xl border border-slate-300 bg-white px-4 py-3.5 text-left shadow-sm transition active:bg-slate-50">
                        <span className="min-w-0">
                          <span className="flex items-center gap-2">
                            <span className="text-[15px] font-semibold text-slate-900">{payNo(p.paymentId)}</span>
                            <span className={`badge ${canceled ? "badge-bad" : "badge-ok"}`}>{canceled ? "취소" : "완료"}</span>
                          </span>
                          <span className="mt-0.5 block text-[13px] text-slate-500">{fmtShort(p.completedAt)}</span>
                        </span>
                        <span className="shrink-0 text-right">
                          <span className={`block text-[17px] font-bold tabular-nums ${canceled ? "text-slate-400 line-through" : "text-brand-700"}`}>{won(p.amount)}</span>
                          <span className="mt-1 inline-flex items-center gap-1 rounded-full bg-brand-600 px-3 py-1 text-[12px] font-bold text-white">
                            영수증 보기
                            <span aria-hidden className="text-sm leading-none">›</span>
                          </span>
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
            {status !== "ALL" && data && data.payments.totalPages > 1 && (
              <p className="note">상태 필터는 지금 보고 있는 페이지의 결제에만 적용돼요.</p>
            )}
            {data && data.payments.totalPages > 1 && (
              <div className="flex items-center justify-between pt-1">
                <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">이전</button>
                <span className="note tabular-nums">{page + 1} / {data.payments.totalPages}</span>
                <button type="button" disabled={page + 1 >= data.payments.totalPages} onClick={() => setPage(page + 1)} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">다음</button>
              </div>
            )}
          </>
        )}
      </div>

      {selected && <ReceiptModal {...receiptProps(selected)} />}
    </>
  );
}

type ReceiptProps = {
  p: MerchantPaymentItem; asking: boolean; busy: boolean; notice: Notice | null;
  onAsk: () => void; onBack: () => void; onCancel: () => void; onClose: () => void;
};

// 영수증 내용. 당일결제내역에서는 창(ReceiptModal) 안에, 과거 결제 취소에서는 조회 칸 아래에 그대로 놓인다
function ReceiptCard({ p, asking, busy, notice, onAsk, onBack, onCancel, onClose, modal = false }: ReceiptProps & { modal?: boolean }) {
  const canceled = p.status === "CANCELED";
  return (
    <section className={modal ? "" : "card !p-5"} aria-label="결제 영수증">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-bold">결제 영수증</h2>
        <span className="rounded-lg bg-slate-100 px-3 py-1.5 text-[13px] font-bold tabular-nums text-slate-800">결제번호 {payNo(p.paymentId)}</span>
      </div>

      <div className="mt-6 text-center">
        <div className={`text-3xl font-bold tabular-nums ${canceled ? "text-slate-400 line-through" : "text-slate-900"}`}>{won(p.amount)}</div>
        <span className={`badge mt-2 ${canceled ? "badge-bad" : "badge-ok"}`}>{canceled ? "결제 취소" : "결제 완료"}</span>
      </div>

      <dl className="mt-5 divide-y divide-slate-200 border-t border-slate-200 text-[15px]">
        <div className="flex justify-between py-3"><dt className="text-slate-500">데브머니 사용</dt><dd className="font-medium tabular-nums">{won(p.walletUsedAmount)}</dd></div>
        <div className="flex justify-between py-3"><dt className="text-slate-500">캐시백 사용</dt><dd className="font-medium tabular-nums">{won(p.cashbackUsedAmount)}</dd></div>
        <div className="flex justify-between py-3"><dt className="text-slate-500">결제 시각</dt><dd className="font-medium tabular-nums">{fmtFull(p.completedAt)}</dd></div>
        {canceled && (
          <div className="flex justify-between py-3 text-red-600"><dt className="font-semibold">취소 시각</dt><dd className="font-semibold tabular-nums">{fmtFull(p.canceledAt)}</dd></div>
        )}
      </dl>

      {notice && (
        <p role={notice.kind === "error" ? "alert" : "status"} className={`mt-4 rounded-lg px-3 py-2 text-[13px] font-medium ${notice.kind === "error" ? "bg-red-50 text-red-700" : "bg-emerald-50 text-emerald-900"}`}>
          {notice.message}
        </p>
      )}

      {asking && !canceled ? (
        <div role="alertdialog" aria-label="결제 취소 확인" className="mt-5 space-y-3 rounded-xl border border-red-200 bg-red-50 p-4">
          <p className="text-[14px] font-medium text-red-700">{payNo(p.paymentId)} {won(p.amount)} 결제를 전액 취소할까요?</p>
          <div className="flex gap-2.5">
            <button type="button" onClick={onBack} disabled={busy} className="btn btn-outline">돌아가기</button>
            <button type="button" onClick={onCancel} disabled={busy} className="btn !bg-red-600 !text-white hover:!bg-red-700 disabled:opacity-40">{busy ? "취소 중…" : "취소하기"}</button>
          </div>
        </div>
      ) : (
        <div className="mt-5 space-y-2.5">
          {!canceled && <button type="button" onClick={onAsk} className="btn btn-danger">결제 취소</button>}
          <button type="button" onClick={onClose} autoFocus={modal} className={modal ? "btn btn-primary" : "btn btn-outline"}>닫기</button>
        </div>
      )}
    </section>
  );
}

function ReceiptModal(props: ReceiptProps) {
  const { onClose } = props;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-5" onClick={onClose} role="presentation">
      <div role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()} className="max-h-[90vh] w-full max-w-[380px] overflow-y-auto rounded-3xl bg-white p-6 shadow-xl">
        <ReceiptCard {...props} modal />
      </div>
    </div>
  );
}
