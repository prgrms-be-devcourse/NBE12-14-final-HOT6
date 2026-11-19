"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { pay } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { clearPending, loadPending } from "@/lib/pendingPayment";
import { REASON_TEXT, type ExclusionReason } from "@/lib/cashbackPreview";
import type { PaymentResponse } from "@/lib/types";

type State = { kind: "loading" } | { kind: "none" } | { kind: "error"; message: string } | { kind: "ok"; res: PaymentResponse };

const STATUS_LABEL: Record<PaymentResponse["status"], string> = {
  PROCESSING: "처리 중", COMPLETED: "결제 완료", FAILED: "결제 실패", CANCELED: "결제 취소",
};

export default function ReceiptPage() {
  const [state, setState] = useState<State>({ kind: "loading" });
  const called = useRef(false);

  useEffect(() => {
    if (called.current) return;
    called.current = true;
    const p = loadPending();
    if (!p) return setState({ kind: "none" });
    pay(p.request, p.key)
      .then((res) => setState({ kind: "ok", res }))
      .catch((e) => setState({ kind: "error", message: e instanceof ApiError ? e.message : "알 수 없는 오류가 발생했습니다" }));
  }, []);

  if (state.kind === "loading") return <p className="px-5 pt-20 text-center text-sm text-zinc-500" role="status">결제 처리 중…</p>;
  if (state.kind === "none")
    return <div className="px-5 pt-20 text-center"><p className="text-sm text-zinc-500">진행 중인 결제가 없습니다</p><Link href="/pay/scan" className="btn btn-primary mt-4">QR 결제로 이동</Link></div>;
  if (state.kind === "error")
    return (
      <div className="flex flex-1 flex-col px-5 pt-16">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-100 text-2xl text-red-800" aria-hidden>!</div>
        <h1 className="mt-3 text-center text-xl font-medium">결제에 실패했습니다</h1>
        <p role="alert" className="mt-2 text-center text-sm text-red-800">{state.message}</p>
        <div className="mt-6 flex gap-2.5"><Link href="/pay/scan" className="btn btn-outline">다시 시도</Link><Link href="/wallet" className="btn btn-primary" onClick={clearPending}>확인</Link></div>
      </div>
    );

  const r = state.res;
  const ok = r.status === "COMPLETED";
  const rows: [string, string][] = [
    ["결제금액", won(r.amount)],
    ["데브머니 사용", won(r.walletUsedAmount)],
    ["캐시백 사용", won(r.cashbackUsedAmount)],
    ["적립 캐시백", r.cashbackEarnedAmount ? won(r.cashbackEarnedAmount) : "없음"],
    ["적용요율", r.appliedRate != null ? `${r.appliedRate}%` : "-"],
    ["계산식", r.appliedRate != null && r.cashbackEarnedAmount ? `${r.walletUsedAmount.toLocaleString("ko-KR")} × ${r.appliedRate}%` : "-"],
    ["지급 예정일", r.cashbackPayableAt ? r.cashbackPayableAt.slice(0, 10) : "-"],
  ];
  const reason = r.cashbackExclusionReason as ExclusionReason | null;
  const excluded = !!reason && reason !== "NONE";

  return (
    <div className="flex flex-1 flex-col px-5 pt-10">
      <div className={`mx-auto flex h-14 w-14 items-center justify-center rounded-full text-2xl ${ok ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-800"}`} aria-hidden>{ok ? "✓" : "…"}</div>
      <h1 className="mt-3 text-center text-xl font-medium">{STATUS_LABEL[r.status]}</h1>
      <p className="amt-out mt-1 text-center text-3xl font-bold">{won(r.amount)}</p>
      <section className="card mt-6">
        <div className="mb-2 font-semibold">확정 영수증</div>
        <dl>{rows.map(([k, v]) => <div key={k} className="flex justify-between border-t border-zinc-100 py-2 text-sm"><dt className="text-zinc-500">{k}</dt><dd className={k === "적립 캐시백" && r.cashbackEarnedAmount ? "amt-cb font-semibold" : ""}>{v}</dd></div>)}</dl>
        {excluded && <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-[13px] text-amber-900">적립 제외 · {REASON_TEXT[reason as ExclusionReason] ?? reason}</p>}
        <p className="note mt-2">소수점 이하는 버림 · 접수 시각 기준 요율로 확정</p>
        <p className="note mt-1">결제번호 PAY-{String(r.paymentId).padStart(4, "0")} (멱등키로 결과 재조회)</p>
      </section>
      <div className="mt-5 flex gap-2.5">
        <Link href="/pay/history" className="btn btn-danger">결제 취소</Link>
        <Link href="/wallet" className="btn btn-primary" onClick={clearPending}>확인</Link>
      </div>
      <p className="note mt-3 text-center">취소 시 지갑 환급 · 캐시백 적립 취소 (취소 API 연동 전)</p>
    </div>
  );
}
