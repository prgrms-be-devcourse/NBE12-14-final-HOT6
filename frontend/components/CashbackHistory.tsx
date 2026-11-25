"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import Header from "@/components/Header";
import HistoryCard from "@/components/HistoryCard";
import MonthlyCashbackCard from "@/components/MonthlyCashbackCard";
import Pager from "@/components/Pager";
import ReceiptModal from "@/components/ReceiptModal";
import { EXCLUSION_TEXT, STATUS_BADGE, STATUS_LABEL, getCashbackSummary, getNotices, mmdd, pctText, type Earning } from "@/lib/cashbackApi";
import { won } from "@/lib/format";
import { fmt, getCashbackHistory, PAGE_SIZE, type CashbackItem } from "@/lib/history";
import type { UserPaymentItem } from "@/lib/types";
import { useLoad } from "@/lib/useLoad";

const FILTERS: { label: string; match: (item: CashbackItem) => boolean }[] = [
  { label: "전체", match: () => true },
  { label: "예정", match: (i) => i.kind === "earning" && i.earning.status === "PENDING" },
  { label: "지급완료", match: (i) => i.kind === "earning" && i.earning.status === "PAID" },
  { label: "사용", match: (i) => i.kind === "use" },
  { label: "제외", match: (i) => i.kind === "earning" && i.earning.status === "EXCLUDED" },
];

// 적립 건의 아랫줄: 접수 시각 뒤에 지급 예정일·지급일·제외 사유를 붙인다
function earningDetail(e: Earning) {
  const extra =
    e.status === "PENDING" && e.payableAt ? `지급 예정 ${mmdd(e.payableAt)}`
    : e.status === "PAID" && e.paidAt ? `지급 완료 ${mmdd(e.paidAt)}`
    : e.status === "EXCLUDED" ? EXCLUSION_TEXT[e.exclusionReason ?? "NONE"]
    : "";
  return extra ? `${fmt(e.requestedAt)} · ${extra}` : fmt(e.requestedAt);
}

type Receipt = { payment: UserPaymentItem; payableAt?: string | null };

// 누르면 그 적립·사용이 생긴 결제의 영수증을 연다
function CashbackCard({ item, onOpen }: { item: CashbackItem; onOpen: (receipt: Receipt) => void }) {
  const { payment, payableAt } = item;
  const open = payment ? () => onOpen({ payment, payableAt }) : undefined;
  if (item.kind === "use") {
    return (
      <HistoryCard
        onClick={open}
        title={item.title}
        amount={`-${won(item.amount)}`}
        amountClass={item.canceled ? "text-slate-400 line-through" : "amt-out"}
        detail={fmt(item.at)}
        badge={item.canceled ? "취소" : "사용"}
        badgeClass={item.canceled ? "badge-bad" : ""}
      />
    );
  }
  const e = item.earning;
  const earned = e.status === "PENDING" || e.status === "PAID";
  return (
    <HistoryCard
      onClick={open}
      title={item.title}
      amount={earned ? `+${won(e.amount)}` : won(e.amount)}
      amountClass={earned ? "amt-cb" : e.status === "EXCLUDED" ? "text-slate-400" : "text-slate-400 line-through"}
      detail={earningDetail(e)}
      badge={STATUS_LABEL[e.status]}
      badgeClass={STATUS_BADGE[e.status]}
    />
  );
}

// 캐시백 화면(/cashback): 맨 위에 이번 달 적립 한도, 아래에 캐시백 내역.
// 하단 캐시백 탭, 홈의 캐시백 잔액, 마이페이지의 캐시백 내역이 모두 이 화면으로 들어온다
export default function CashbackHistory() {
  const [filter, setFilter] = useState(0);
  const [page, setPage] = useState(0);
  const [sel, setSel] = useState<Receipt | null>(null);
  const close = useCallback(() => setSel(null), []);
  const summary = useLoad(getCashbackSummary, []);
  const load = useLoad(getCashbackHistory, []);
  const notices = useLoad(() => getNotices(undefined, 0, 1), []);
  const s = summary.data;
  const notice = notices.data?.content[0];
  const rows = load.data?.filter(FILTERS[filter].match) ?? [];
  const totalPages = Math.ceil(rows.length / PAGE_SIZE);
  const current = Math.min(page, Math.max(0, totalPages - 1));

  return (
    <>
      <Header title="캐시백 내역" right={s ? `${s.year}년 ${s.month}월` : ""} />
      <div className="px-5 pb-4">
        {notice && (
          <Link href="/cashback/notices" className="mb-3 block rounded-xl border border-amber-500 bg-amber-50 px-4 py-3 text-[13px] text-amber-900">
            <span className="font-semibold">공지</span> · {notice.content}
            {notice.rate != null && notice.previousRate != null && ` (요율 ${pctText(notice.previousRate)} → ${pctText(notice.rate)})`}
            <span className="ml-1 underline">전체 보기</span>
          </Link>
        )}
        <MonthlyCashbackCard summary={s} />
        <div className="grid grid-cols-3 gap-2 text-center">
          {[["적립 예정", s ? won(s.pendingAmount) : "–"], ["지급 완료", s ? won(s.paidAmount) : "–"], ["제외", s ? `${s.excludedCount}건` : "–"]].map(([k, v]) => (
            <div key={k} className="card-gray !p-3">
              <div className="note">{k}</div>
              <div className="mt-0.5 font-semibold">{v}</div>
            </div>
          ))}
        </div>
        <div className="my-4 flex flex-wrap gap-2" role="group" aria-label="내역 필터">
          {FILTERS.map((x, i) => (
            <button key={x.label} type="button" aria-pressed={filter === i} onClick={() => { setFilter(i); setPage(0); }} className={`chip ${filter === i ? "chip-on" : ""}`}>
              {x.label}
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
          {rows.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE).map((item) => <CashbackCard key={item.key} item={item} onOpen={setSel} />)}
        </ul>
        <Pager page={current} totalPages={totalPages} onChange={setPage} />
      </div>
      {sel && <ReceiptModal payment={sel.payment} payableAt={sel.payableAt} onClose={close} />}
    </>
  );
}
