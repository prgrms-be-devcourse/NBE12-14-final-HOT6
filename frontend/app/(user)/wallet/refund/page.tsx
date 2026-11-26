"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import Header from "@/components/Header";
import { ApiError } from "@/lib/api";
import { getMyWallet } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";
import { idemKeyFor, refundWallet } from "@/lib/walletApi";

export default function RefundPage() {
  const router = useRouter();
  const wallet = useLoad(getMyWallet, []);
  const [amount, setAmount] = useState(30000);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const keyRef = useRef<{ amount: number; key: string } | null>(null);

  // 환불은 데브머니만 가능하다 (캐시백·적립 예정은 환불할 수 없다)
  const max = wallet.data?.devMoney ?? 0;
  const set = (v: string) => setAmount(Math.min(max, Number(v.replace(/\D/g, "")) || 0));

  const submit = async () => {
    setBusy(true);
    setError("");
    try {
      await refundWallet(amount, idemKeyFor(keyRef, amount));
      router.push(`/wallet?refunded=${amount}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "환불하지 못했어요. 잠시 뒤 다시 시도해 주세요");
      setBusy(false);
    }
  };

  return (
    <>
      <Header title="환불" back="/wallet" />
      <div className="space-y-4 px-5">
        {wallet.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{wallet.error}</p>}
        <div className="card-gray flex items-baseline justify-between">
          <span className="text-[13px] text-zinc-500">환불 가능 잔액</span>
          <span className="text-lg font-semibold text-slate-900">{wallet.data ? won(max) : "…"}</span>
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
          <p className="note mt-2">데브머니만 환불할 수 있어요. 캐시백은 환불되지 않아요.</p>
        </div>
        <div>
          <span className="label">환불 받을 계좌</span>
          <div className="card !py-3">
            <div className="text-[15px]">모의계좌 (가입 시 생성)</div>
          </div>
        </div>
        <div className="flex items-baseline justify-between border-t border-zinc-200 pt-3">
          <span className="text-[13px] text-zinc-500">환불 후 잔액</span>
          <span className="text-xl font-bold text-slate-900">{wallet.data ? won(max - amount) : "…"}</span>
        </div>
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{error}</p>}
        <button type="button" onClick={submit} disabled={busy || amount === 0 || !wallet.data} className="btn btn-danger disabled:opacity-40">{busy ? "환불 중…" : "환불하기"}</button>
      </div>
    </>
  );
}
