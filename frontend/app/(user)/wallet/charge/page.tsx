"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import Header from "@/components/Header";
import { ApiError } from "@/lib/api";
import { getMyWallet } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";
import { chargeWallet, getAccountBalance, idemKeyFor } from "@/lib/walletApi";

export default function ChargePage() {
  const router = useRouter();
  const [amount, setAmount] = useState(50000);
  const [busy, setBusy] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const keyRef = useRef<{ amount: number; key: string } | null>(null);
  const wallet = useLoad(getMyWallet, []);
  const account = useLoad(getAccountBalance, []);
  const w = wallet.data;

  // 충전 가능액은 월 충전 한도와 보유 한도 중 더 빡빡한 쪽이다 (서버가 최종 판단하고, 여기서는 미리 안내한다)
  const monthlyLeft = w ? Math.max(0, w.monthlyChargeLimit - w.chargedThisMonth) : 0;
  const holdingLeft = w ? Math.max(0, w.holdingLimit - w.devMoney) : 0;
  const accountBalance = account.data?.balance;
  const error = !w ? ""
    : accountBalance !== undefined && amount > accountBalance ? `모의계좌 잔액이 부족해요 (잔액 ${won(accountBalance)})`
    : amount > monthlyLeft ? `이번 달 충전 한도를 넘어요 (남은 한도 ${won(monthlyLeft)})`
    : amount > holdingLeft ? `지갑 보유 한도를 넘어요 (${won(w.holdingLimit)}까지 보유 가능 · 충전 가능 ${won(holdingLeft)})`
    : "";
  const set = (v: string) => setAmount(Math.max(0, Number(v.replace(/\D/g, "")) || 0));
  const blocked = !w || amount === 0 || !!error || busy;

  // PG 결제 없이 바로 충전한다: 서버가 모의계좌에서 지갑으로 옮긴다. 같은 금액으로 다시 눌러도 같은 멱등키라 한 번만 반영된다
  const submit = async () => {
    setBusy(true);
    setSubmitError("");
    try {
      await chargeWallet(amount, idemKeyFor(keyRef, amount));
      router.push(`/wallet?charged=${amount}`);
    } catch (e) {
      setSubmitError(e instanceof ApiError ? e.message : "충전하지 못했어요. 잠시 뒤 다시 시도해 주세요");
      setBusy(false);
    }
  };

  return (
    <>
      <Header title="충전" back="/wallet" />
      <div className="space-y-4 px-5">
        {wallet.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{wallet.error}</p>}
        <div className="card-gray flex items-baseline justify-between">
          <span className="text-[13px] text-zinc-500">현재 잔액</span>
          <span className="text-lg font-semibold text-slate-900">{w ? won(w.devMoney) : "…"}</span>
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
          {w && <p className="note mt-2">이번 달 충전 {won(w.chargedThisMonth)} / {won(w.monthlyChargeLimit)} · 남은 한도 {won(monthlyLeft)}</p>}
        </div>
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{error}</p>}
        <div>
          <span className="label">충전 수단</span>
          <div className="card !py-3 text-[15px]">
            모의계좌에서 충전
            <div className="note mt-0.5">계좌 잔액 {account.data ? won(account.data.balance) : account.error ? "불러오지 못했어요" : "…"}</div>
          </div>
        </div>
        <div className="flex items-baseline justify-between border-t border-zinc-200 pt-3">
          <span className="text-[13px] text-zinc-500">충전 후 잔액</span>
          <span className="amt-in text-xl font-bold">{w ? won(w.devMoney + amount) : "…"}</span>
        </div>
        {accountBalance !== undefined && (
          <div className="flex items-baseline justify-between">
            <span className="text-[13px] text-zinc-500">충전 후 모의계좌 잔액</span>
            <span className="text-[15px] font-semibold tabular-nums text-slate-900">{won(Math.max(0, accountBalance - amount))}</span>
          </div>
        )}
        {submitError && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{submitError}</p>}
        <button type="button" onClick={submit} disabled={blocked} className="btn btn-primary disabled:opacity-40">{busy ? "충전 중…" : "충전하기"}</button>
      </div>
    </>
  );
}
