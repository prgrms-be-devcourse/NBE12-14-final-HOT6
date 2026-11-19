"use client";

import Link from "next/link";
import { useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { LIMITS } from "@/lib/limits";
import { wallet } from "@/lib/mock";

export default function ChargePage() {
  const [amount, setAmount] = useState(50000);
  const monthlyLeft = LIMITS.monthlyCharge - wallet.monthlyCharged;
  const holdingLeft = LIMITS.holding - wallet.devMoney;
  const error =
    amount > monthlyLeft ? `이번 달 충전 한도를 넘어요 (남은 한도 ${won(monthlyLeft)})`
    : amount > holdingLeft ? `지갑 보유 한도를 넘어요 (${won(LIMITS.holding)}까지 보유 가능 · 충전 가능 ${won(holdingLeft)})`
    : "";
  const set = (v: string) => setAmount(Math.max(0, Number(v.replace(/\D/g, "")) || 0));
  return (
    <>
      <Header title="충전" back="/wallet" />
      <div className="space-y-4 px-5">
        <div className="card-gray flex items-baseline justify-between">
          <span className="text-[13px] text-zinc-500">현재 잔액</span>
          <span className="text-lg font-semibold text-blue-900">{won(wallet.devMoney)}</span>
        </div>
        <div>
          <label htmlFor="amt" className="label">충전 금액</label>
          <div className="flex items-center gap-2">
            <input id="amt" inputMode="numeric" className="field text-right text-lg font-medium" value={amount.toLocaleString("ko-KR")} onChange={(e) => set(e.target.value)} />
            <span>원</span>
          </div>
          <div className="mt-2 flex gap-2">
            {[10000, 50000, 100000].map((v) => (
              <button key={v} type="button" onClick={() => setAmount((a) => a + v)} className="chip">+{v.toLocaleString("ko-KR")}</button>
            ))}
          </div>
        </div>
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{error}</p>}
        <div>
          <span className="label">충전 수단</span>
          <div className="card !py-3 text-[15px]">PG 결제 (카드 · 간편결제)</div>
        </div>
        <div className="flex items-baseline justify-between border-t border-zinc-200 pt-3">
          <span className="text-[13px] text-zinc-500">충전 후 잔액</span>
          <span className="amt-in text-xl font-bold">{won(wallet.devMoney + amount)}</span>
        </div>
        <Link href={`/wallet/pg?amount=${amount}`} aria-disabled={amount === 0 || !!error} className={`btn btn-primary ${amount === 0 || error ? "pointer-events-none opacity-40" : ""}`}>충전하기</Link>
        <div className="card-dash">
          <ul className="space-y-0.5">
            <li>• 월 충전 한도 {won(LIMITS.monthlyCharge)} · 이번 달 {won(wallet.monthlyCharged)} 사용</li>
            <li>• 지갑 보유 한도 {won(LIMITS.holding)}</li>
            <li>• 충전하기 → PG 결제창으로 이동</li>
            <li>• 승인 완료 후 지갑에 잔액 반영</li>
          </ul>
        </div>
      </div>
    </>
  );
}
