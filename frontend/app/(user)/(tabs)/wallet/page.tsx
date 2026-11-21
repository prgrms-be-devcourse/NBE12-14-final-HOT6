import Link from "next/link";
import { Suspense } from "react";
import Amount from "@/components/Amount";
import Header from "@/components/Header";
import Progress from "@/components/Progress";
import TxRow from "@/components/TxRow";
import { won } from "@/lib/format";
import { wallet, walletTxs, type Tx } from "@/lib/mock";

export default function WalletPage({ searchParams }: { searchParams: Promise<{ charged?: string }> }) {
  return (
    <>
      <Header title="내 지갑" />
      <Suspense fallback={null}>
        <WalletBody searchParams={searchParams} />
      </Suspense>
    </>
  );
}

async function WalletBody({ searchParams }: { searchParams: Promise<{ charged?: string }> }) {
  const { charged } = await searchParams;
  const amount = Number(charged);
  const done = Number.isInteger(amount) && amount > 0;

  const txs: Tx[] = done
    ? [{ id: "new", title: "충전 · PG", date: "11.17 15:02", amount, kind: "charge", isNew: true }, ...walletTxs.slice(0, 3)]
    : walletTxs;
  const balance = wallet.devMoney + (done ? amount : 0);

  return (
    <>
      <div className="px-5">
        {done && (
          <p role="status" className="mb-3 rounded-lg bg-brand-600 py-2.5 text-center text-[13px] font-bold text-white">
            {won(amount)} 충전이 완료되었습니다
          </p>
        )}
        <section className="card !p-5">
          <span className="text-[13px] font-medium text-slate-500">데브머니 잔액</span>
          <div className="mt-0.5"><Amount value={balance} size="xl" /></div>
          <div className="mt-2 flex items-baseline justify-between border-t border-slate-200 pt-2.5">
            <span className="text-[13px] text-slate-500">캐시백 잔액</span>
            <Amount value={wallet.cashbackBalance} kind="cashback" size="md" />
          </div>
          <div className="mt-4 flex gap-2.5">
            <Link href="/wallet/charge" className="btn btn-primary">충전</Link>
            <Link href="/wallet/refund" className="btn btn-danger">환불</Link>
          </div>
        </section>

        <section className="card mt-3">
          <div className="flex justify-between">
            <span className="font-bold">이번 달 캐시백 적립</span>
            <span className="text-[13px]">
              {wallet.monthlyCashback.toLocaleString("ko-KR")} / {won(wallet.monthlyLimit)}
            </span>
          </div>
          <div className="mt-2.5"><Progress value={wallet.monthlyCashback} max={wallet.monthlyLimit} /></div>
          <div className="mt-3 flex items-baseline justify-between rounded-lg bg-emerald-50 px-3 py-2">
            <span className="text-[13px] font-semibold text-emerald-800">월 적립 한도 잔여</span>
            <span className="amt-cb text-base font-bold">{won(wallet.monthlyLimit - wallet.monthlyCashback)}</span>
          </div>
          <p className="note mt-1.5">월 적립 한도 {won(wallet.monthlyLimit)} 중 {won(wallet.monthlyCashback)} 적립 (적립 예정 포함)</p>
        </section>

        <div className="mx-1.5 mb-1.5 mt-6 flex items-baseline justify-between">
          <h2 className="text-base font-bold">거래내역</h2>
          <span className="note tabular-nums">{txs[0].date.slice(0, 5)} ~ {txs[txs.length - 1].date.slice(0, 5)} · {txs.length}건</span>
        </div>
        <ul className="pb-4">
          {txs.map((t) => <TxRow key={t.id} tx={t} />)}
        </ul>
      </div>
    </>
  );
}
