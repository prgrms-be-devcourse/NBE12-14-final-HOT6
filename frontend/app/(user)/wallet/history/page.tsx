"use client";

import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";
import Header from "@/components/Header";
import { HelpButton, HelpTip } from "@/components/Help";
import HistoryCard from "@/components/HistoryCard";
import Pager from "@/components/Pager";
import PaymentCard from "@/components/PaymentCard";
import ReceiptModal from "@/components/ReceiptModal";
import { won } from "@/lib/format";
import { fmt, getUsageHistory, PAGE_SIZE, type UsageItem } from "@/lib/history";
import type { UserPaymentItem } from "@/lib/types";
import { useLoad } from "@/lib/useLoad";

type Filter = { label: string; match: (item: UsageItem) => boolean };

// 탭마다 고를 수 있는 필터. 첫 번째가 그 탭의 전체다
const TABS: { label: string; filters: Filter[] }[] = [
  {
    label: "전체 내역",
    filters: [
      { label: "전체", match: () => true },
      { label: "결제 내역", match: (i) => i.kind === "payment" },
    ],
  },
  {
    label: "데브머니 내역",
    filters: [
      { label: "전체", match: (i) => i.kind !== "payment" },
      { label: "충전", match: (i) => i.kind === "charge" },
      { label: "환불", match: (i) => i.kind === "refund" },
    ],
  },
];

// 홈의 데브머니 잔액 옆 버튼으로 들어오는 이용내역. 결제를 누르면 영수증이 열린다
function UsageHistory() {
  const [tab, setTab] = useState(0);
  const [filter, setFilter] = useState(0);
  const [page, setPage] = useState(0);
  const [help, setHelp] = useState(false);
  const [sel, setSel] = useState<UserPaymentItem | null>(null);
  const close = useCallback(() => setSel(null), []);
  const load = useLoad(getUsageHistory, []);

  const { filters } = TABS[tab];
  const rows = load.data?.filter(filters[filter].match) ?? [];
  const totalPages = Math.ceil(rows.length / PAGE_SIZE);
  const current = Math.min(page, Math.max(0, totalPages - 1));

  const pickTab = (i: number) => {
    setTab(i);
    setFilter(0);
    setPage(0);
  };

  return (
    <>
      <Header title="이용내역" back="/wallet" />
      <div className="-mt-4 grid grid-cols-2 border-b border-slate-200 text-[13px]" role="tablist" aria-label="이용내역 종류">
        {TABS.map((t, i) => (
          <div key={t.label} className={`flex h-11 items-center justify-center gap-1.5 border-b-[3px] ${tab === i ? "border-brand-600" : "border-transparent"}`}>
            <button type="button" role="tab" aria-selected={tab === i} onClick={() => pickTab(i)} className={`self-stretch ${i === 0 ? "flex-1" : "pl-3"} ${tab === i ? "font-bold text-brand-600" : "text-slate-400"}`}>
              {t.label}
            </button>
            {i === 1 && (
              <HelpButton open={help} onClick={() => setHelp((v) => !v)} controls="devmoney-help" label="데브머니 내역 안내 보기" />
            )}
          </div>
        ))}
      </div>
      <HelpTip id="devmoney-help" open={help} className="mx-5 mt-3">
        데브머니 내역은 지갑에서 충전과 환불만 노출
      </HelpTip>

      <div className="px-5 pb-4 pt-3">
        <div className="mb-4 flex gap-2" role="group" aria-label="내역 필터">
          {filters.map((f, i) => (
            <button key={f.label} type="button" aria-pressed={filter === i} onClick={() => { setFilter(i); setPage(0); }} className={`chip ${filter === i ? "chip-on" : ""}`}>
              {f.label}
            </button>
          ))}
        </div>

        {load.loading && !load.data && <p role="status" className="note py-6 text-center">내역을 불러오는 중이에요…</p>}
        {load.error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">
            {load.error} <button type="button" onClick={load.reload} className="underline">다시 불러오기</button>
          </p>
        )}
        {load.data && rows.length === 0 && <p className="note py-6 text-center">내역이 없어요</p>}

        <ul className="space-y-2.5">
          {rows.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE).map((item) =>
            item.kind === "payment" ? (
              <PaymentCard key={item.key} payment={item.payment} onClick={() => setSel(item.payment)} />
            ) : (
              <HistoryCard
                key={item.key}
                title={item.kind === "charge" ? "계좌에서 충전" : "계좌로 환불"}
                amount={won(item.amount)}
                amountClass={item.kind === "charge" ? "amt-in" : "amt-out"}
                detail={`${fmt(item.at)} · 잔액 ${won(item.balanceAfter)}`}
                badge={item.kind === "charge" ? "충전" : "환불"}
                badgeClass={item.kind === "charge" ? "badge-info" : ""}
              />
            ),
          )}
        </ul>
        <Pager page={current} totalPages={totalPages} onChange={setPage} />
      </div>
      {sel && <ReceiptModal payment={sel} onClose={close} />}
    </>
  );
}

export default function UsageHistoryPage() {
  // 새로 들어올 때는 전체 내역 탭부터 보이고, 뒤로 가기로 돌아오면 보던 상태를 유지한다
  const { bfcacheId } = useRouter();
  return <UsageHistory key={bfcacheId} />;
}
