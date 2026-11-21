"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";

const METHODS = ["카드", "계좌이체", "간편결제"];

function PgForm() {
  const amount = Number(useSearchParams().get("amount")) || 50000;
  const [method, setMethod] = useState("카드");
  return (
    <div className="space-y-4 px-5">
      <div className="card-gray">
        <div className="note">주문 정보</div>
        <div className="mt-1 flex items-baseline justify-between"><span className="font-medium">지갑 충전</span><span className="font-semibold">{won(amount)}</span></div>
        <div className="note mt-0.5">주문번호 CHG-0001</div>
      </div>
      <div className="flex gap-2" role="tablist">
        {METHODS.map((m) => (
          <button key={m} type="button" role="tab" aria-selected={method === m} onClick={() => setMethod(m)} className={`chip flex-1 ${method === m ? "chip-on" : ""}`}>{m}</button>
        ))}
      </div>
      <div className="space-y-3">
        <label className="block"><span className="label">카드사 선택</span>
          <select className="field"><option>카드사를 선택하세요</option><option>○○카드</option><option>△△카드</option></select></label>
        <label className="block"><span className="label">카드번호</span><input className="field" placeholder="0000-0000-0000-0000" inputMode="numeric" /></label>
        <div className="flex gap-2">
          <label className="block flex-1"><span className="label">유효기간</span><input className="field" placeholder="MM/YY" /></label>
          <label className="block flex-1"><span className="label">CVC</span><input className="field" placeholder="•••" inputMode="numeric" /></label>
        </div>
        <label className="block"><span className="label">할부</span><select className="field"><option>일시불</option></select></label>
        <label className="flex items-center gap-2 text-[13px]"><input type="checkbox" className="h-4 w-4" /> 결제 정보 제공 동의</label>
      </div>
      <Link href={`/wallet?charged=${amount}`} className="btn btn-primary">{won(amount)} 결제</Link>
    </div>
  );
}

export default function PgPage() {
  return (
    <>
      <Header title="PG 결제" back="/wallet/charge" />
      <Suspense fallback={null}><PgForm /></Suspense>
    </>
  );
}
