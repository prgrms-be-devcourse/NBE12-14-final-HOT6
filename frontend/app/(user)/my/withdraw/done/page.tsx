"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import Header from "@/components/Header";
import LogoutLink from "@/components/LogoutLink";
import { ApiError } from "@/lib/api";
import { won } from "@/lib/format";
import { settleWithdrawal, type WithdrawalResult } from "@/lib/userApi";
import { clearWithdrawal, loadWithdrawal } from "@/lib/withdrawPending";

type State = { kind: "loading" } | { kind: "none" } | { kind: "error"; message: string } | { kind: "ok"; res: WithdrawalResult };

// 간편비밀번호를 확인한 뒤 탈퇴를 실행한다. 같은 멱등키로 다시 보내도 한 번만 처리된다
export default function WithdrawDonePage() {
  const router = useRouter();
  const [state, setState] = useState<State>({ kind: "loading" });
  const called = useRef(false);

  useEffect(() => {
    if (called.current) return;
    called.current = true;
    const p = loadWithdrawal();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 확인한 정산 정보는 마운트 후 세션 저장소에서 읽는다
    if (!p) return setState({ kind: "none" });
    settleWithdrawal(p.expected, p.key).then(
      (res) => { clearWithdrawal(); setState({ kind: "ok", res }); },
      (e: unknown) => {
        // 확인한 뒤 금액이 달라졌으면 새 금액으로 다시 확인받는다
        if (e instanceof ApiError && e.code === "AUTH-008") { clearWithdrawal(); return router.replace("/my/withdraw?changed=1"); }
        setState({ kind: "error", message: e instanceof ApiError ? e.message : "탈퇴하지 못했어요. 잠시 뒤 다시 시도해 주세요" });
      },
    );
  }, [router]);

  if (state.kind === "loading") return <><Header title="회원 탈퇴" /><p className="px-5 pt-20 text-center text-sm text-zinc-500" role="status">탈퇴 처리 중…</p></>;
  if (state.kind === "none")
    return <><Header title="회원 탈퇴" back="/my" /><div className="px-5 pt-20 text-center"><p className="text-sm text-zinc-500">확인한 정산 내용이 없어요</p><Link href="/my/withdraw" className="btn btn-primary mt-4">정산 내용 확인하기</Link></div></>;
  if (state.kind === "error")
    return (
      <>
        <Header title="회원 탈퇴" back="/my" />
        <div className="px-5 pt-16 text-center">
          <h1 className="text-xl font-bold">탈퇴하지 못했어요</h1>
          <p role="alert" className="mt-2 text-sm text-red-800">{state.message}</p>
          <Link href="/my/withdraw" className="btn btn-outline mt-6">정산 내용 다시 확인</Link>
        </div>
      </>
    );
  const r = state.res;
  return (
    <>
      <Header title="회원 탈퇴" />
      <div className="space-y-4 px-5 pt-10">
        <h1 className="text-center text-xl font-bold">탈퇴가 완료되었어요</h1>
        <section className="card space-y-2 text-sm">
          <div className="flex justify-between"><span>모의계좌로 환급</span><b>{won(r.refundedAmount)}</b></div>
          <div className="flex justify-between"><span>소멸된 적립 예정 캐시백{r.canceledEarningCount > 0 && ` (${r.canceledEarningCount}건)`}</span><b>{won(r.canceledEarningAmount)}</b></div>
        </section>
        <LogoutLink href="/login" className="btn btn-primary">로그인 화면으로</LogoutLink>
      </div>
    </>
  );
}
