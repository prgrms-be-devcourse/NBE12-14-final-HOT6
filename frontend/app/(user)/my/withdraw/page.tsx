"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";
import { previewWithdrawal } from "@/lib/userApi";
import { newWithdrawal, saveWithdrawal } from "@/lib/withdrawPending";

function WithdrawBody() {
  const router = useRouter();
  const changed = useSearchParams().get("changed") === "1";
  const [ok, setOk] = useState(false);
  const preview = useLoad(previewWithdrawal, []);
  const p = preview.data;

  // 확인한 환불 예정액을 멱등키와 함께 두고, 간편비밀번호 확인 뒤 실행 화면에서 정산한다
  const go = () => {
    if (!p) return;
    saveWithdrawal(newWithdrawal(p.refundAmount));
    router.push("/pin?next=/my/withdraw/done");
  };

  return (
    <div className="space-y-4 px-5 pb-5">
      {changed && (
        <div role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-[13px] text-amber-900">
          <b>정산 금액이 달라졌어요</b>
          <p className="mt-0.5">확인한 뒤 결제·충전이 있었어요. 아래 금액으로 다시 확인해 주세요.</p>
        </div>
      )}
      <div className="rounded-xl border border-red-300 bg-red-50 p-4">
        <div className="font-semibold text-red-800">! 탈퇴 시 계정과 지갑이 정리됩니다</div>
        <p className="note mt-0.5">아래 정산 내용을 확인해 주세요</p>
      </div>
      {preview.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{preview.error}</p>}
      {preview.loading && <p className="note text-center" role="status">정산 내용을 불러오는 중이에요…</p>}
      {p && (
        <>
          <section className="card space-y-3">
            <div className="text-sm font-semibold">정산 내역</div>
            <div><div className="flex justify-between"><span>지갑 잔액</span><span className="font-semibold">{won(p.refundAmount)}</span></div><div className="note">→ 모의계좌로 환급</div></div>
            <div><div className="flex justify-between"><span>적립 예정 캐시백{p.pendingCashbackCount > 0 && ` (${p.pendingCashbackCount}건)`}</span><span className="font-semibold">{won(p.pendingCashbackAmount)}</span></div><div className="note">→ 탈퇴하면 소멸돼요</div></div>
            <div><div className="flex justify-between"><span>캐시백 잔액</span><span className={`font-semibold ${p.cashbackBalance < 0 ? "amt-out" : ""}`}>{won(p.cashbackBalance)}</span></div><div className="note">{p.cashbackBalance < 0 ? "→ 정산하지 않고 그대로 탈퇴돼요" : "→ 탈퇴하면 더 이상 쓸 수 없어요"}</div></div>
          </section>
          <label className="flex items-center gap-2 text-[13px]"><input type="checkbox" className="h-4 w-4" checked={ok} onChange={(e) => setOk(e.target.checked)} /> 위 정산 내용을 확인했습니다</label>
          <button type="button" onClick={go} disabled={!ok} className="btn btn-danger disabled:opacity-40">간편비밀번호 확인 · 탈퇴하기</button>
        </>
      )}
      <Link href="/my" className="btn btn-outline">취소</Link>
    </div>
  );
}

export default function WithdrawPage() {
  return (
    <>
      <Header title="회원 탈퇴 · 정산" back="/my" />
      <Suspense fallback={null}><WithdrawBody /></Suspense>
    </>
  );
}
