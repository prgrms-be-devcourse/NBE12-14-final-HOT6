"use client";

import { useCallback, useState } from "react";
import Header from "@/components/Header";
import Pager from "@/components/Pager";
import PaymentCard from "@/components/PaymentCard";
import ReceiptModal from "@/components/ReceiptModal";
import { getMyPayments } from "@/lib/endpoints";
import { PAGE_SIZE } from "@/lib/history";
import { useLoad } from "@/lib/useLoad";
import type { UserPaymentItem } from "@/lib/types";

// 탭 이름 → 서버 status (전체는 생략)
const FILTERS = [
  { label: "전체", status: undefined },
  { label: "완료", status: "COMPLETED" },
  { label: "취소", status: "CANCELED" },
] as const;

// 내 결제 내역. 결제 완료·취소만 최신순으로 보이고, 지갑은 서버가 토큰의 회원으로 찾는다
export default function PayHistoryPage() {
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState<UserPaymentItem | null>(null);
  const close = useCallback(() => setSel(null), []);
  const f = FILTERS[tab];
  const load = useLoad(() => getMyPayments(f.status, page, PAGE_SIZE), [tab, page]);
  const data = load.data?.payments;

  const pick = (i: number) => {
    setTab(i);
    setPage(0);
    setSel(null);
  };

  return (
    <>
      <Header title="결제 내역" back="/my" />
      <div className="px-5 pb-4">
        <div className="mb-4 flex gap-2" role="tablist">
          {FILTERS.map((x, i) => (
            <button key={x.label} type="button" role="tab" aria-selected={tab === i} onClick={() => pick(i)} className={`chip ${tab === i ? "chip-on" : ""}`}>
              {x.label}
            </button>
          ))}
        </div>

        {load.loading && !data && <p role="status" className="note py-6 text-center">결제 내역을 불러오는 중이에요…</p>}
        {load.error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">
            {load.error} <button type="button" onClick={load.reload} className="underline">다시 불러오기</button>
          </p>
        )}
        {data && data.content.length === 0 && <p className="note py-6 text-center">결제 내역이 없어요</p>}

        <ul className="space-y-2.5">
          {data?.content.map((p) => <PaymentCard key={p.paymentId} payment={p} onClick={() => setSel(p)} />)}
        </ul>

        {data && <Pager page={page} totalPages={data.totalPages} onChange={setPage} />}
      </div>
      {sel && <ReceiptModal payment={sel} onClose={close} />}
    </>
  );
}
