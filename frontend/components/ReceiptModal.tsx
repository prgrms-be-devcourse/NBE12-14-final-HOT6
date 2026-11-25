"use client";

import { useEffect, type ReactNode } from "react";
import { won } from "@/lib/format";
import { fmtFull } from "@/lib/history";
import type { UserPaymentItem } from "@/lib/types";

const payNo = (id: number) => `PAY-${String(id).padStart(4, "0")}`;

type Props = {
  payment: UserPaymentItem;
  payableAt?: string | null; // 캐시백 지급 예정일. 캐시백 내역에서 지급 전인 적립이 있을 때만 넘긴다
  onClose: () => void;
};

// 결제 영수증 창. 바깥을 누르거나 Esc 로 닫는다
export default function ReceiptModal({ payment: sel, payableAt, onClose }: Props) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-5" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label="결제 영수증" onClick={(e) => e.stopPropagation()} className="w-full max-w-[390px] rounded-2xl bg-white px-5 pb-5 pt-5">
        <div className="flex items-center justify-between gap-2">
          <span className="font-bold">결제 영수증</span>
          <span className="rounded-md bg-slate-100 px-2.5 py-1 text-[13px] font-bold tabular-nums text-slate-800">결제번호 {payNo(sel.paymentId)}</span>
        </div>
        <div className="mt-4 text-center">
          <div className="font-medium">{sel.merchantName ?? "알 수 없는 가맹점"}</div>
          <div className={`mt-1 text-[32px] font-extrabold tabular-nums tracking-tight ${sel.status === "COMPLETED" ? "amt-out" : "text-slate-400 line-through"}`}>{won(sel.amount)}</div>
          <span className={`badge mt-1 ${sel.status === "COMPLETED" ? "badge-ok" : "badge-bad"}`}>{sel.status === "COMPLETED" ? "결제 완료" : "결제 취소"}</span>
        </div>
        <dl className="mt-4 text-sm">
          {([
            ["데브머니 사용", won(sel.walletUsedAmount)],
            ["캐시백 사용", won(sel.cashbackUsedAmount)],
            ["적립 예정 캐시백", sel.cashbackEarnedAmount ? sel.status === "CANCELED" ? (<><span className="line-through">{won(sel.cashbackEarnedAmount)}</span> 적립 예정 취소</>) : `+${won(sel.cashbackEarnedAmount)}` : "없음"],
            ...(payableAt && sel.status === "COMPLETED" ? ([["지급 예정일", payableAt.slice(0, 10)]] as [string, string][]) : []),
            ["결제 시각", fmtFull(sel.completedAt)],
            ...(sel.status === "CANCELED" ? ([["취소 시각", fmtFull(sel.canceledAt)]] as [string, string][]) : []),
          ] as [string, ReactNode][]).map(([k, v]) => (
            <div key={k} className="flex justify-between border-t border-slate-100 py-2.5">
              <dt className={k === "취소 시각" ? "font-semibold text-red-600" : "text-slate-500"}>{k}</dt>
              <dd className={`font-medium tabular-nums ${k === "취소 시각" ? "font-bold text-red-600" : k === "적립 예정 캐시백" && sel.cashbackEarnedAmount ? (sel.status === "CANCELED" ? "text-red-600" : "text-emerald-700") : ""}`}>{v}</dd>
            </div>
          ))}
        </dl>
        <button type="button" onClick={onClose} className="btn btn-primary mt-3">닫기</button>
      </div>
    </div>
  );
}
