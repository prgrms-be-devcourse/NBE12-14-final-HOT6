"use client";

import { useState } from "react";
import TxRow from "@/components/TxRow";
import { useLoad } from "@/lib/useLoad";
import { accountRow, type AccountTx, getAccountTransactions } from "@/lib/walletApi";

const PAGE_SIZE = 10;

// 모의계좌 거래내역: 가입 보너스·충전·환불, 최신순, 더 보기. 날짜 옆에 거래 직후 계좌 잔액을 함께 보여 준다
export default function AccountTxList() {
  const first = useLoad(() => getAccountTransactions(0, PAGE_SIZE), []);
  const [more, setMore] = useState<{ rows: AccountTx[]; page: number; busy: boolean; error: string }>({ rows: [], page: 0, busy: false, error: "" });

  if (first.loading) return <p className="note px-1.5 py-6 text-center" role="status">계좌 내역을 불러오는 중이에요…</p>;
  if (first.error || !first.data) return <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{first.error ?? "계좌 내역을 불러오지 못했어요"}</p>;

  const rows = [...first.data.content, ...more.rows];
  const hasMore = more.page + 1 < first.data.totalPages;

  const loadMore = async () => {
    setMore((m) => ({ ...m, busy: true, error: "" }));
    try {
      const next = more.page + 1;
      const res = await getAccountTransactions(next, PAGE_SIZE);
      setMore((m) => ({ rows: [...m.rows, ...res.content], page: next, busy: false, error: "" }));
    } catch (e) {
      setMore((m) => ({ ...m, busy: false, error: e instanceof Error ? e.message : "더 불러오지 못했어요" }));
    }
  };

  return (
    <>
      {rows.length === 0 ? (
        <p className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">계좌 거래내역이 없어요.</p>
      ) : (
        <ul>{rows.map((t) => <TxRow key={t.transactionId} tx={accountRow(t)} />)}</ul>
      )}
      {more.error && <p role="alert" className="mt-2 text-[13px] font-medium text-red-600">{more.error}</p>}
      {hasMore && <button type="button" onClick={loadMore} disabled={more.busy} className="btn btn-outline btn-sm mt-3 disabled:opacity-40">{more.busy ? "불러오는 중…" : "더 보기"}</button>}
    </>
  );
}
