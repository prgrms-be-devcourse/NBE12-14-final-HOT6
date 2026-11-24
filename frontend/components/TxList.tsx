"use client";

import { useState } from "react";
import TxRow from "@/components/TxRow";
import { useLoad } from "@/lib/useLoad";
import { getWalletTransactions, groupOf, toRow, TX_GROUPS, type TxGroup, type WalletTx } from "@/lib/walletApi";

const PAGE_SIZE = 20;

// 지갑 거래내역: 최신순, 종류별 필터, 더 보기
export default function TxList() {
  const first = useLoad(() => getWalletTransactions(0, PAGE_SIZE), []);
  const [more, setMore] = useState<{ rows: WalletTx[]; page: number; busy: boolean; error: string }>({ rows: [], page: 0, busy: false, error: "" });
  const [group, setGroup] = useState<TxGroup>("전체");

  if (first.loading) return <p className="note px-1.5 py-6 text-center" role="status">거래내역을 불러오는 중이에요…</p>;
  if (first.error || !first.data) return <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{first.error ?? "거래내역을 불러오지 못했어요"}</p>;

  const all = [...first.data.content, ...more.rows];
  const hasMore = more.page + 1 < first.data.totalPages;
  const rows = all.filter((t) => group === "전체" || groupOf(t.type) === group);

  const loadMore = async () => {
    setMore((m) => ({ ...m, busy: true, error: "" }));
    try {
      const next = more.page + 1;
      const res = await getWalletTransactions(next, PAGE_SIZE);
      setMore((m) => ({ rows: [...m.rows, ...res.content], page: next, busy: false, error: "" }));
    } catch (e) {
      setMore((m) => ({ ...m, busy: false, error: e instanceof Error ? e.message : "더 불러오지 못했어요" }));
    }
  };

  return (
    <>
      <div className="mb-2 flex gap-2 overflow-x-auto" role="tablist" aria-label="거래 종류">
        {TX_GROUPS.map((g) => (
          <button key={g} type="button" role="tab" aria-selected={group === g} onClick={() => setGroup(g)} className={`chip shrink-0 ${group === g ? "chip-on" : ""}`}>{g}</button>
        ))}
      </div>
      {rows.length === 0 ? (
        <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
          {all.length === 0 ? "아직 거래내역이 없어요. 충전하고 QR로 결제해 보세요." : `${group} 내역이 없어요.`}
        </p>
      ) : (
        <ul>{rows.map((t) => <TxRow key={t.transactionId} tx={toRow(t)} />)}</ul>
      )}
      {more.error && <p role="alert" className="mt-2 text-[13px] font-medium text-red-600">{more.error}</p>}
      {hasMore && <button type="button" onClick={loadMore} disabled={more.busy} className="btn btn-outline btn-sm mt-3 disabled:opacity-40">{more.busy ? "불러오는 중…" : "더 보기"}</button>}
    </>
  );
}
