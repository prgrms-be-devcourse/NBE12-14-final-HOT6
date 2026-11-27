"use client";

import { useCallback, useRef, useState } from "react";
import ConfirmButton from "@/components/ConfirmButton";
import Pager from "@/components/Pager";
import PayoutRunModal from "@/components/PayoutRunModal";
import { getPayoutRuns, getPayoutStatus, runPayouts, type PayoutRunResult } from "@/lib/adminApi";
import { won } from "@/lib/format";
import { payoutDateTime, TRIGGER_TEXT } from "@/lib/payoutText";
import { useLoad } from "@/lib/useLoad";

const PAGE_SIZE = 20;

// 운영자의 적립 지급 배치: 지급 대기 현황, 수동 실행, 실행 목록과 상세
export default function PayoutBatch({ onRan }: { onRan: () => void }) {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [result, setResult] = useState<PayoutRunResult | null>(null);
  const running = useRef(false); // 확인 버튼을 거듭 눌러도 한 번만 실행한다
  const status = useLoad(() => getPayoutStatus(), []);
  const runs = useLoad(() => getPayoutRuns(page, PAGE_SIZE), [page]);
  const close = useCallback(() => setSelected(null), []);
  const s = status.data;
  const data = runs.data;
  const reload = () => {
    status.reload();
    runs.reload();
  };

  const run = async () => {
    if (running.current) return;
    running.current = true;
    setResult(null);
    try {
      setResult(await runPayouts());
      setPage(0);
      reload();
      onRan();
    } finally {
      running.current = false;
    }
  };

  return (
    <div className="space-y-5">
      <section className="card space-y-3 !p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-bold">지급 대기 현황</h2>
          <button type="button" onClick={reload} disabled={status.loading || runs.loading} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">새로고침</button>
        </div>
        {status.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{status.error}</p>}
        {!s && status.loading && <p role="status" className="note">지급 현황을 불러오는 중…</p>}
        {s && (
          <>
            <dl className="grid gap-3 sm:grid-cols-3">
              <div className="rounded-lg bg-slate-50 px-3 py-2.5">
                <dt className="note">지급 대기</dt>
                <dd className="mt-0.5 text-lg font-bold tabular-nums">{s.pendingCount.toLocaleString("ko-KR")}건 · {won(s.pendingAmount)}</dd>
              </div>
              <div className={`rounded-lg px-3 py-2.5 ${s.dueCount > 0 ? "bg-amber-50" : "bg-slate-50"}`}>
                <dt className="note">지금 지급 가능</dt>
                <dd className={`mt-0.5 text-lg font-bold tabular-nums ${s.dueCount > 0 ? "text-amber-800" : ""}`}>{s.dueCount.toLocaleString("ko-KR")}건 · {won(s.dueAmount)}</dd>
              </div>
              <div className="rounded-lg bg-slate-50 px-3 py-2.5">
                <dt className="note">다음 지급 예정</dt>
                <dd className="mt-0.5 text-lg font-bold tabular-nums">{payoutDateTime(s.nextPayableAt)}</dd>
              </div>
            </dl>
            <div className="flex flex-wrap items-center gap-3">
              <ConfirmButton label="지금 지급 실행" message="지급 예정 시각이 지난 적립을 지금 지갑에 입금해요. 진행할까요?" onConfirm={run} className="btn btn-primary !w-auto px-6" />
              {result && (
                <p role="status" className={`rounded-lg px-3 py-2 text-[13px] font-medium ${result.failed > 0 ? "bg-red-50 text-red-700" : "bg-emerald-50 text-emerald-800"}`}>
                  지급 {result.paid}건 · 넘어감 {result.skipped}건 · 실패 {result.failed}건
                </p>
              )}
            </div>
            <p className="note">실행한 뒤에도 &quot;지금 지급 가능&quot;에 남는 건은 지급에 실패한 건이며 다음 실행에서 다시 시도해요.</p>
          </>
        )}
      </section>

      <section className="card overflow-x-auto !p-5">
        <h2 className="mb-2 text-lg font-bold">실행 기록</h2>
        {runs.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{runs.error}</p>}
        {!data && runs.loading && <p role="status" className="note">실행 기록을 불러오는 중…</p>}
        {data && data.content.length === 0 && <p className="note">아직 실행 기록이 없어요.</p>}
        {data && data.content.length > 0 && (
          <table className="table">
            <thead><tr><th>실행 시각</th><th>방식</th><th>지급</th><th>넘어감</th><th>실패</th></tr></thead>
            <tbody>
              {data.content.map((r) => (
                <tr key={r.runId} onClick={() => setSelected(r.runId)} className="cursor-pointer hover:bg-slate-50">
                  <td className="tabular-nums">
                    {/* 줄 어디를 눌러도 열리고, 키보드는 이 버튼으로 연다 */}
                    <button type="button" className="font-medium text-brand-600 underline" aria-label={`${payoutDateTime(r.ranAt)} 실행 상세 보기`}>{payoutDateTime(r.ranAt)}</button>
                  </td>
                  <td>{TRIGGER_TEXT[r.trigger]}</td>
                  <td className="tabular-nums">{r.paid}건</td>
                  <td className="tabular-nums">{r.skipped}건</td>
                  <td className={`tabular-nums ${r.failed > 0 ? "font-bold text-red-600" : ""}`}>{r.failed}건</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {data && <Pager page={page} totalPages={data.totalPages} onChange={setPage} />}
        <p className="note mt-2">줄을 누르면 처리한 적립과 실패 사유를 볼 수 있어요. 자동 실행은 지급이나 실패가 있었던 것만 남아요.</p>
      </section>

      {selected != null && <PayoutRunModal runId={selected} onClose={close} />}
    </div>
  );
}
