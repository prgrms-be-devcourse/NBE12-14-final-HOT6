"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api";
import { cancelPayment } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { paymentErrorText } from "@/lib/paymentErrors";
import type { PaymentResponse } from "@/lib/types";

type State =
  | { kind: "idle" }
  | { kind: "ask" }
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ok"; res: PaymentResponse };

const fmt = (iso?: string | null) => (iso ? iso.slice(0, 16).replace("T", " ") : "-");

// 가맹점주 결제 취소 (전액). 결제 목록 API 가 생기기 전까지는 영수증의 결제번호(숫자)를 입력해 취소한다
export default function CancelPaymentForm() {
  const [paymentId, setPaymentId] = useState("");
  const [state, setState] = useState<State>({ kind: "idle" });

  const id = Number(paymentId);
  const validId = Number.isInteger(id) && id > 0;

  const run = async () => {
    setState({ kind: "loading" });
    try {
      setState({ kind: "ok", res: await cancelPayment(id) });
    } catch (e) {
      setState({ kind: "error", message: e instanceof ApiError ? paymentErrorText(e.code, e.message) : paymentErrorText() });
    }
  };

  return (
    <section className="card">
      <div className="mb-2 font-bold">결제 취소</div>
      <label className="block">
        <span className="label">결제번호 (영수증의 PAY-0001 → 1)</span>
        <input
          className="field tabular-nums"
          inputMode="numeric"
          value={paymentId}
          onChange={(e) => { setPaymentId(e.target.value.replace(/[^0-9]/g, "")); setState({ kind: "idle" }); }}
          placeholder="예: 1"
        />
      </label>

      {state.kind === "ask" ? (
        <div role="alertdialog" aria-label="결제 취소 확인" className="mt-3 flex flex-wrap items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2">
          <span className="text-[13px] font-medium text-red-700">PAY-{String(id).padStart(4, "0")} 결제를 전액 취소할까요?</span>
          <button type="button" onClick={() => setState({ kind: "idle" })} className="btn btn-outline btn-sm !w-auto">돌아가기</button>
          <button type="button" onClick={run} className="btn btn-sm !w-auto !bg-red-600 !text-white hover:!bg-red-700">취소하기</button>
        </div>
      ) : (
        <button type="button" disabled={!validId || state.kind === "loading"} onClick={() => setState({ kind: "ask" })} className="btn btn-danger mt-3 disabled:opacity-40">
          {state.kind === "loading" ? "취소 중…" : "결제 취소"}
        </button>
      )}

      {state.kind === "error" && <p role="alert" className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{state.message}</p>}
      {state.kind === "ok" && (
        <div role="status" className="mt-2 rounded-lg bg-emerald-50 px-3 py-2 text-[13px] text-emerald-900">
          <b>{state.res.status === "CANCELED" ? "결제가 취소됐어요" : "처리됐어요"}</b> · {won(state.res.amount)} · 취소 시각 {fmt(state.res.canceledAt)}
          <div className="note mt-1">데브머니 {won(state.res.walletUsedAmount)}, 캐시백 {won(state.res.cashbackUsedAmount)}이 돌아갔어요</div>
        </div>
      )}
    </section>
  );
}
