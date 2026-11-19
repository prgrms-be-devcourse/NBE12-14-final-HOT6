"use client";

import Link from "next/link";
import { useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { wallet } from "@/lib/mock";

export default function WithdrawPage() {
  const [ok, setOk] = useState(false);
  return (
    <>
      <Header title="회원 탈퇴 · 정산" back="/my" />
      <div className="space-y-4 px-5 pb-5">
        <div className="rounded-xl border border-red-300 bg-red-50 p-4">
          <div className="font-semibold text-red-800">! 탈퇴 시 계정과 지갑이 정리됩니다</div>
          <p className="note mt-0.5">아래 정산 내용을 확인해 주세요</p>
        </div>
        <section className="card space-y-3">
          <div className="text-sm font-semibold">정산 내역</div>
          <div><div className="flex justify-between"><span>지갑 잔액</span><span className="font-semibold">{won(wallet.devMoney)}</span></div><div className="note">→ 모의계좌로 환급</div></div>
          <div><div className="flex justify-between"><span>지급 예정 캐시백</span><span className="font-semibold">{won(wallet.pendingCashback)}</span></div><div className="note">→ 정산 기준 확인 필요</div></div>
        </section>
        <label className="flex items-center gap-2 text-[13px]"><input type="checkbox" className="h-4 w-4" checked={ok} onChange={(e) => setOk(e.target.checked)} /> 위 정산 내용을 확인했습니다</label>
        <Link href="/pin?next=/login" aria-disabled={!ok} className={`btn btn-danger ${ok ? "" : "pointer-events-none opacity-40"}`}>간편비밀번호 확인 · 탈퇴하기</Link>
        <Link href="/my" className="btn btn-outline">취소</Link>
      </div>
    </>
  );
}
