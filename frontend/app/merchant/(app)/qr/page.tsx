"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import Countdown from "@/components/Countdown";
import Header from "@/components/Header";
import { LIMITS } from "@/lib/limits";
import { won } from "@/lib/format";

type St = "대기중" | "결제완료" | "만료";

export default function MerchantQrPage() {
  const [amount, setAmount] = useState(12000);
  const [gen, setGen] = useState<number | null>(null);
  const [status, setStatus] = useState<St>("대기중");
  const expire = useCallback(() => setStatus((s) => (s === "대기중" ? "만료" : s)), []);

  const create = () => { setStatus("대기중"); setGen(Date.now()); };

  return (
    <>
      <Header title="QR 생성" />
      <div className="space-y-4 px-5 pb-5">
        <div>
          <label htmlFor="amt" className="label">결제 금액</label>
          <div className="flex items-center gap-2">
            <input id="amt" inputMode="numeric" className="field text-right text-lg font-medium" value={amount.toLocaleString("ko-KR")} onChange={(e) => setAmount(Number(e.target.value.replace(/\D/g, "")) || 0)} />
            <span>원</span>
          </div>
        </div>
        <div className="-mt-2 flex flex-wrap gap-2">
          {[1000, 5000, 10000].map((v) => <button key={v} type="button" onClick={() => setAmount((a) => a + v)} className="chip">+{v.toLocaleString("ko-KR")}</button>)}
          <button type="button" onClick={() => setAmount(0)} className="chip">지우기</button>
        </div>
        {amount > LIMITS.perPayment && <p role="alert" className="text-[13px] font-medium text-red-600">결제 한도({won(LIMITS.perPayment)})를 넘는 금액은 QR을 만들 수 없어요</p>}
        <button type="button" onClick={create} disabled={amount === 0 || amount > LIMITS.perPayment} className="btn btn-primary disabled:opacity-40">QR 생성</button>
        {gen && (
          <section className="card text-center">
            <div className={`mx-auto grid h-44 w-44 grid-cols-7 gap-0.5 rounded-lg border-2 border-brand-600 p-2 ${status === "대기중" ? "" : "opacity-30"}`} aria-label="QR 코드 자리">
              {Array.from({ length: 49 }, (_, i) => <span key={i} className={((i * 7 + (i >> 2)) % 3 === 0) ? "bg-slate-800" : "bg-slate-100"} />)}
            </div>
            <p className="mt-3 text-sm">남은 시간 {status === "대기중" ? <Countdown key={gen} seconds={179} onExpire={expire} className="font-semibold" /> : <span className="font-semibold">00:00</span>}</p>
          </section>
        )}
        <div>
          <span className="label">QR 상태조회</span>
          <div className="flex gap-2">
            {(["대기중", "결제완료", "만료"] as St[]).map((s) => (
              <span key={s} className={`chip flex-1 text-center ${gen && status === s ? "chip-on" : ""}`}>{s}</span>
            ))}
          </div>
        </div>
        {gen && status === "대기중" && (
          <Link href="/merchant/paid" className="btn btn-outline btn-sm">(데모) 회원이 결제함</Link>
        )}
      </div>
    </>
  );
}
