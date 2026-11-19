"use client";

import Link from "next/link";
import { useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { wallet } from "@/lib/mock";

export default function RefundPage() {
  const [amount, setAmount] = useState(30000);
  const max = wallet.devMoney;
  const set = (v: string) => setAmount(Math.min(max, Number(v.replace(/\D/g, "")) || 0));
  return (
    <>
      <Header title="환불" back="/wallet" />
      <div className="space-y-4 px-5">
        <div className="card-gray flex items-baseline justify-between">
          <span className="text-[13px] text-zinc-500">환불 가능 잔액</span>
          <span className="text-lg font-semibold text-blue-900">{won(max)}</span>
        </div>
        <div>
          <label htmlFor="amt" className="label">환불 금액</label>
          <div className="flex items-center gap-2">
            <input id="amt" inputMode="numeric" className="field amt-out text-right text-lg font-semibold" value={amount.toLocaleString("ko-KR")} onChange={(e) => set(e.target.value)} />
            <span>원</span>
          </div>
          <div className="mt-2 flex gap-2">
            {[10000, 50000].map((v) => (
              <button key={v} type="button" onClick={() => setAmount((a) => Math.min(max, a + v))} className="chip">{v.toLocaleString("ko-KR")}</button>
            ))}
            <button type="button" onClick={() => setAmount(max)} className="chip">전액</button>
          </div>
        </div>
        <div>
          <span className="label">환불 받을 계좌</span>
          <div className="card !py-3"><div className="text-[15px]">모의계좌 (가입 시 생성)</div><div className="note">국민 ***-1234</div></div>
        </div>
        <div className="flex items-baseline justify-between border-t border-zinc-200 pt-3">
          <span className="text-[13px] text-zinc-500">환불 후 잔액</span>
          <span className="text-xl font-bold text-blue-900">{won(max - amount)}</span>
        </div>
        <Link href="/wallet" aria-disabled={amount === 0} className={`btn btn-danger ${amount === 0 ? "pointer-events-none opacity-40" : ""}`}>환불하기</Link>
        <div className="card-dash">
          <ul className="space-y-0.5">
            <li>• 환불 금액은 모의계좌로 입금</li>
            <li>• 거래내역에 환불로 기록</li>
          </ul>
        </div>
      </div>
    </>
  );
}
