"use client";

import { useEffect } from "react";
import { getPayoutRun, type PayoutItemResult } from "@/lib/adminApi";
import { payoutDateTime, TRIGGER_TEXT } from "@/lib/payoutText";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

const RESULT: Record<PayoutItemResult, { label: string; badge: string }> = {
  FAILED: { label: "실패", badge: "badge-bad" },
  SKIPPED: { label: "넘어감", badge: "badge-warn" },
  PAID: { label: "지급", badge: "badge-ok" },
};

// 지급 배치 실행 한 번의 상세 창. 바깥을 누르거나 Esc 로 닫는다
export default function PayoutRunModal({ runId, onClose }: { runId: number; onClose: () => void }) {
  const load = useLoad(() => getPayoutRun(runId), [runId]);
  // 다른 실행을 열면 새로 불러오기 전까지 이전 실행의 내용을 보여 주지 않는다
  const d = load.data?.run.runId === runId ? load.data : null;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label="지급 실행 상세" onClick={(e) => e.stopPropagation()} className="flex max-h-[85vh] w-full max-w-3xl flex-col rounded-2xl bg-white p-5">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-lg font-bold">지급 실행 상세</h2>
          <button type="button" onClick={onClose} className="btn btn-outline btn-sm !w-auto">닫기</button>
        </div>
        {load.error && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{load.error}</p>}
        {!d && !load.error && <p role="status" className="note mt-3">불러오는 중…</p>}
        {d && (
          <>
            <p className="mt-2 text-sm text-slate-600">
              {payoutDateTime(d.run.ranAt)} · {TRIGGER_TEXT[d.run.trigger]} 실행
            </p>
            <dl className="mt-3 grid gap-2 sm:grid-cols-4">
              {([
                ["지급", `${d.run.paid}건 · ${won(d.paidAmount)}`],
                ["넘어감", `${d.run.skipped}건`],
                ["실패", `${d.run.failed}건`],
                ["처리한 적립", `${d.items.length}건`],
              ] as const).map(([k, v]) => (
                <div key={k} className="rounded-lg bg-slate-50 px-3 py-2">
                  <dt className="note">{k}</dt>
                  <dd className={`mt-0.5 font-bold tabular-nums ${k === "실패" && d.run.failed > 0 ? "text-red-600" : ""}`}>{v}</dd>
                </div>
              ))}
            </dl>
            <div className="mt-3 overflow-auto">
              {d.items.length === 0 ? (
                <p className="note">처리한 적립이 없어요.</p>
              ) : (
                <table className="table">
                  <thead><tr><th>결과</th><th>적립 번호</th><th>결제 번호</th><th>지갑 번호</th><th>금액</th><th>실패 사유</th></tr></thead>
                  <tbody>
                    {d.items.map((i) => (
                      <tr key={i.earningId}>
                        <td><span className={`badge ${RESULT[i.result].badge}`}>{RESULT[i.result].label}</span></td>
                        <td className="tabular-nums">{i.earningId}</td>
                        <td className="tabular-nums">PAY-{String(i.paymentId).padStart(4, "0")}</td>
                        <td className="tabular-nums">{i.walletId}</td>
                        <td className="tabular-nums">{won(i.amount)}</td>
                        <td>{i.errorCode ? `${i.errorMessage} (${i.errorCode})` : "-"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
