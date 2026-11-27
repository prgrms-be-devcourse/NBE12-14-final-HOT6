"use client";

import { useState } from "react";
import Pager from "@/components/Pager";
import { getAdminEarnings, type EarningFilter } from "@/lib/adminApi";
import { EXCLUSION_TEXT, pctText, STATUS_BADGE, STATUS_LABEL, type EarningStatus } from "@/lib/cashbackApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

const PAGE_SIZE = 20;
const EMPTY: EarningFilter = { status: "", walletId: "", from: "", to: "" };
const STATUSES = Object.keys(STATUS_LABEL) as EarningStatus[];
const dateTime = (iso: string | null) => (iso ? iso.slice(0, 16).replace("T", " ") : "-");

// 운영자의 적립 내역: 모든 이용자의 적립을 상태, 접수일, 지갑 번호로 찾아본다. 보기만 한다.
// refresh 가 바뀌면(지급 배치를 실행하면) 같은 조건으로 다시 조회한다
export default function EarningList({ refresh }: { refresh: number }) {
  const [draft, setDraft] = useState<EarningFilter>(EMPTY); // 입력 중인 조건
  const [filter, setFilter] = useState<EarningFilter>(EMPTY); // 조회에 적용한 조건
  const [page, setPage] = useState(0);
  const load = useLoad(() => getAdminEarnings(filter, page, PAGE_SIZE), [filter.status, filter.walletId, filter.from, filter.to, page, refresh]);
  const data = load.data;
  const error = draft.from && draft.to && draft.from > draft.to ? "시작일이 종료일보다 늦어요" : draft.walletId === "0" ? "지갑 번호는 1 이상이에요" : "";
  const set = (patch: Partial<EarningFilter>) => setDraft((d) => ({ ...d, ...patch }));
  // 조건이나 쪽을 바꾸면 불러오는 중으로 표시한다. 같은 조건으로 다시 눌러도 새로 조회한다
  const apply = (next: EarningFilter, nextPage = 0) => {
    setFilter(next);
    setPage(nextPage);
    load.reload();
  };

  return (
    <div className="space-y-5">
      <form
        className="card flex flex-wrap items-end gap-3 !p-5"
        aria-label="조회 조건"
        onSubmit={(e) => {
          e.preventDefault();
          if (!error) apply(draft);
        }}
      >
        <label className="block">
          <span className="label">상태</span>
          <select className="field !w-32" value={draft.status} onChange={(e) => set({ status: e.target.value as EarningFilter["status"] })}>
            <option value="">전체</option>
            {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </label>
        <label className="block">
          <span className="label">접수일부터</span>
          <input type="date" className="field !w-40" value={draft.from} onChange={(e) => set({ from: e.target.value })} />
        </label>
        <label className="block">
          <span className="label">접수일까지</span>
          <input type="date" className="field !w-40" value={draft.to} onChange={(e) => set({ to: e.target.value })} />
        </label>
        <label className="block">
          <span className="label">지갑 번호</span>
          <input className="field !w-28 tabular-nums" inputMode="numeric" placeholder="전체" value={draft.walletId} onChange={(e) => set({ walletId: e.target.value.replace(/\D/g, "").slice(0, 15) })} />
        </label>
        <button type="submit" disabled={!!error} className="btn btn-primary !w-auto px-6 disabled:opacity-40">조회</button>
        <button type="button" onClick={() => { setDraft(EMPTY); apply(EMPTY); }} className="btn btn-outline !w-auto px-4">초기화</button>
        {error && <p role="alert" className="w-full text-[13px] font-medium text-red-600">{error}</p>}
      </form>

      <section className="card overflow-x-auto !p-5">
        <h2 className="mb-2 text-lg font-bold">조회 결과 {data && <span className="note">{data.totalElements.toLocaleString("ko-KR")}건</span>}</h2>
        {load.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{load.error}</p>}
        {load.loading && <p role="status" className="note">적립 내역을 불러오는 중…</p>}
        {!load.loading && data && data.content.length === 0 && <p className="note">조건에 맞는 적립이 없어요.</p>}
        {!load.loading && data && data.content.length > 0 && (
          <table className="table whitespace-nowrap">
            <thead><tr><th>접수 시각</th><th>지갑</th><th>결제</th><th>상태</th><th>기준 금액</th><th>요율</th><th>적립액</th><th>처리</th></tr></thead>
            <tbody>
              {data.content.map((e) => (
                <tr key={e.earningId}>
                  <td className="tabular-nums">{dateTime(e.requestedAt)}</td>
                  <td className="tabular-nums">
                    <button type="button" className="font-medium text-brand-600 underline" aria-label={`${e.walletId}번 지갑만 보기`} onClick={() => { const next = { ...filter, walletId: String(e.walletId) }; setDraft(next); apply(next); }}>{e.walletId}</button>
                  </td>
                  <td className="tabular-nums">{e.paymentId}</td>
                  <td><span className={`badge ${STATUS_BADGE[e.status]}`}>{STATUS_LABEL[e.status]}</span></td>
                  <td className="tabular-nums">{won(e.walletUsedAmount)}</td>
                  <td className="tabular-nums">{e.status === "EXCLUDED" ? "-" : pctText(e.appliedRate)}</td>
                  <td className="font-bold tabular-nums">{won(e.amount)}</td>
                  <td className="tabular-nums">
                    {e.status === "EXCLUDED" ? EXCLUSION_TEXT[e.exclusionReason ?? "NONE"]
                      : e.status === "PENDING" ? `${dateTime(e.payableAt)} 지급 예정`
                      : e.status === "PAID" ? `${dateTime(e.paidAt)} 지급`
                      : `${dateTime(e.canceledAt)} ${STATUS_LABEL[e.status]}`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {data && <Pager page={page} totalPages={data.totalPages} onChange={(next) => apply(filter, next)} />}
        <p className="note mt-2">지갑 번호를 누르면 그 지갑의 적립만 볼 수 있어요. 접수일은 결제를 접수한 날이에요.</p>
      </section>
    </div>
  );
}
