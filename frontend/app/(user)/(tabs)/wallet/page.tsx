"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import Amount from "@/components/Amount";
import Header from "@/components/Header";
import Progress from "@/components/Progress";
import TxList from "@/components/TxList";
import { getCashbackSummary, getMonthlyLimit } from "@/lib/cashbackApi";
import { getMyWallet } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

// 충전·환불을 마치고 돌아오면 ?charged=금액 / ?refunded=금액 으로 완료 안내를 띄운다
function doneBanner(params: URLSearchParams) {
  const charged = Number(params.get("charged"));
  const refunded = Number(params.get("refunded"));
  if (Number.isInteger(charged) && charged > 0) return `${won(charged)} 충전이 완료되었습니다`;
  if (Number.isInteger(refunded) && refunded > 0) return `${won(refunded)} 환불이 완료되었습니다`;
  return null;
}

function WalletBody() {
  const banner = doneBanner(useSearchParams());
  const wallet = useLoad(getMyWallet, []);
  const summary = useLoad(getCashbackSummary, []);
  const limit = useLoad(getMonthlyLimit, []);
  const w = wallet.data;

  return (
    <div className="px-5">
      {banner && <p role="status" className="mb-3 rounded-lg bg-brand-600 py-2.5 text-center text-[13px] font-bold text-white">{banner}</p>}
      {wallet.error && <p role="alert" className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{wallet.error}</p>}
      <section className="card !p-5">
        <span className="text-[13px] font-medium text-slate-500">데브머니 잔액</span>
        <div className="mt-0.5">{w ? <Amount value={w.devMoney} size="xl" /> : <span className="note">불러오는 중…</span>}</div>
        <div className="mt-2 flex items-baseline justify-between border-t border-slate-200 pt-2.5">
          <span className="text-[13px] text-slate-500">캐시백 잔액</span>
          {w ? <Amount value={w.cashbackBalance} kind={w.cashbackBalance < 0 ? "out" : "cashback"} size="md" /> : <span className="note">…</span>}
        </div>
        {w && w.cashbackBalance < 0 && (
          <p className="note mt-1">캐시백 잔액이 음수예요. 다음 적립분으로 먼저 채워지고, 그동안은 캐시백을 쓸 수 없어요.</p>
        )}
        <div className="mt-2 flex items-baseline justify-between">
          <span className="text-[13px] text-slate-500">적립 예정</span>
          {summary.data ? <Amount value={summary.data.pendingAmount} kind="wait" size="md" /> : <span className="note">…</span>}
        </div>
        <p className="note mt-0.5">적립 예정은 지급되기 전까지 쓸 수 없어요</p>
        <div className="mt-4 flex gap-2.5">
          <Link href="/wallet/charge" className="btn btn-primary">충전</Link>
          <Link href="/wallet/refund" className="btn btn-danger">환불</Link>
        </div>
      </section>

      <section className="card mt-3">
        <div className="flex justify-between">
          <span className="font-bold">이번 달 캐시백 적립</span>
          <span className="text-[13px]">{limit.data ? `${limit.data.usedAmount.toLocaleString("ko-KR")} / ${won(limit.data.limit)}` : "…"}</span>
        </div>
        {limit.data && (
          <>
            <div className="mt-2.5"><Progress value={limit.data.usedAmount} max={limit.data.limit} /></div>
            <div className="mt-3 flex items-baseline justify-between rounded-lg bg-emerald-50 px-3 py-2">
              <span className="text-[13px] font-semibold text-emerald-800">월 적립 한도 잔여</span>
              <span className="amt-cb text-base font-bold">{won(limit.data.remaining)}</span>
            </div>
            <p className="note mt-1.5">월 적립 한도 {won(limit.data.limit)} 중 {won(limit.data.usedAmount)} 적립 (적립 예정 포함)</p>
          </>
        )}
        {limit.error && <p role="alert" className="mt-2 text-[13px] text-red-600">{limit.error}</p>}
      </section>

      <h2 className="mx-1.5 mb-1.5 mt-6 text-base font-bold">거래내역</h2>
      <div className="pb-4"><TxList /></div>
    </div>
  );
}

export default function WalletPage() {
  return (
    <>
      <Header title="내 지갑" />
      <Suspense fallback={null}>
        <WalletBody />
      </Suspense>
    </>
  );
}
