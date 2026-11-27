"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import Header from "@/components/Header";
import LogoutLink from "@/components/LogoutLink";
import WithdrawLockedDialog from "@/components/WithdrawLockedDialog";
import { ApiError } from "@/lib/api";
import { won } from "@/lib/format";
import { dropPin, peekPin } from "@/lib/pinStore";
import { checkWithdrawal, isAlreadyWithdrawn, settleWithdrawal, type WithdrawalResult } from "@/lib/userApi";
import { clearWithdrawal, loadWithdrawal, type PendingWithdrawal } from "@/lib/withdrawPending";

const PIN_HREF = "/pin?next=/my/withdraw/done";

type State =
  | { kind: "loading" }
  | { kind: "none" }
  | { kind: "error"; message: string; retry?: "pin" | "send" | "check" } // pin: 간편비밀번호 다시 입력, send: 같은 요청 다시 보내기, check: 탈퇴 여부 다시 확인
  | { kind: "locked" } // 간편비밀번호를 5회 틀려 잠김
  | { kind: "ok"; res: WithdrawalResult | null }; // res 가 없으면 처리 결과는 못 받았지만 탈퇴는 끝난 것

// 서버가 분명히 거절한 4xx 가 아니면(연결 오류, 서버 오류, 깨진 응답) 처리됐는지 알 수 없다
const isUnknownResult = (e: unknown) => !(e instanceof ApiError) || e.status < 400 || e.status >= 500;

// 간편비밀번호를 확인한 뒤 탈퇴를 실행한다. 같은 멱등키로 다시 보내도 한 번만 처리된다
export default function WithdrawDonePage() {
  const router = useRouter();
  const [state, setState] = useState<State>({ kind: "loading" });
  const sending = useRef(false);
  const finished = useRef(false); // 탈퇴가 끝난 뒤에는 다시 들어와도 완료 화면을 그대로 둔다
  const handledKey = useRef<string | null>(null); // 마지막으로 처리한 요청의 멱등키
  const pinForResend = useRef<string | null>(null); // 응답을 못 받았을 때 다시 보내려고 이 화면 안에만 둔다
  const shown = useRef(false); // 화면이 보이는 동안에만 다른 화면으로 보낸다

  const finish = (res: WithdrawalResult | null) => {
    finished.current = true;
    pinForResend.current = null;
    clearWithdrawal();
    setState({ kind: "ok", res });
  };

  const send = async (p: PendingWithdrawal, pin: string) => {
    if (sending.current) return;
    sending.current = true;
    handledKey.current = p.key;
    pinForResend.current = pin;
    setState({ kind: "loading" });
    try {
      finish(await settleWithdrawal(p.expected, p.key, pin));
    } catch (e) {
      if (isAlreadyWithdrawn(e)) return finish(null);
      // 결과를 알 수 없으면 같은 요청을 다시 보낼 수 있게 둔다
      if (isUnknownResult(e)) return setState({ kind: "error", message: "응답을 받지 못했어요. 다시 시도해 주세요", retry: "send" });
      pinForResend.current = null;
      const err = e as ApiError;
      // 확인한 뒤 금액이 달라졌으면 새 금액으로 다시 확인받는다
      if (err.code === "AUTH-008") {
        clearWithdrawal();
        if (shown.current) return router.replace("/my/withdraw?changed=1");
        return setState({ kind: "none" });
      }
      if (err.code === "AUTH-006") { clearWithdrawal(); return setState({ kind: "locked" }); }
      if (err.status === 401) { if (shown.current) router.replace("/login"); return; }
      setState({ kind: "error", message: err.message, retry: err.code === "AUTH-005" ? "pin" : undefined });
    } finally {
      sending.current = false;
    }
  };

  // 간편비밀번호는 저장소에 남기지 않아 새로고침하면 사라진다. 응답을 못 받은 채 새로고침했을 수 있어,
  // 다시 입력받기 전에 이미 탈퇴했는지 확인한다
  const askPin = async (key: string) => {
    handledKey.current = key;
    try {
      await checkWithdrawal();
    } catch (e) {
      if (isAlreadyWithdrawn(e)) return finish(null);
      if (isUnknownResult(e))
        return setState({ kind: "error", message: "탈퇴가 끝났는지 확인하지 못했어요. 연결을 확인하고 다시 시도해 주세요", retry: "check" });
      if (e instanceof ApiError && e.status === 401) { if (shown.current) router.replace("/login"); return; }
    }
    // 확인하는 동안 화면을 벗어났으면 끌고 가지 않는다. 다시 들어오면 처음부터 확인한다
    if (!shown.current) { handledKey.current = null; return; }
    setState({ kind: "error", message: "간편비밀번호를 다시 입력해 주세요", retry: "pin" });
    router.replace(PIN_HREF);
  };

  // 화면에 들어올 때마다 확인한다 (cacheComponents 로 이전 화면 상태가 남아 있을 수 있다)
  useEffect(() => {
    shown.current = true;
    const leave = () => { shown.current = false; };
    if (finished.current) return leave;
    const p = loadWithdrawal();
    if (!p) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- 확인한 정산 정보는 마운트 후 세션 저장소에서 읽는다
      setState({ kind: "none" });
      return leave;
    }
    const pin = peekPin();
    if (pin) {
      dropPin();
      void send(p, pin);
    } else if (handledKey.current !== p.key) {
      void askPin(p.key); // 이미 처리한 요청이면 보여 주던 결과를 그대로 둔다
    }
    return leave;
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 화면에 들어올 때 한 번만 실행한다
  }, []);

  const resend = () => {
    const p = loadWithdrawal();
    if (p && pinForResend.current) void send(p, pinForResend.current);
  };

  const recheck = () => {
    const p = loadWithdrawal();
    if (!p) return;
    setState({ kind: "loading" });
    void askPin(p.key);
  };

  if (state.kind === "loading") return <><Header title="회원 탈퇴" /><p className="px-5 pt-20 text-center text-sm text-zinc-500" role="status">탈퇴 처리 중…</p></>;
  if (state.kind === "none")
    return <><Header title="회원 탈퇴" back="/my" /><div className="px-5 pt-20 text-center"><p className="text-sm text-zinc-500">확인한 정산 내용이 없어요</p><Link href="/my/withdraw" className="btn btn-primary mt-4">정산 내용 확인하기</Link></div></>;
  if (state.kind === "locked")
    return <><Header title="회원 탈퇴" back="/my" /><WithdrawLockedDialog onConfirm={() => router.replace("/my")} /></>;
  if (state.kind === "error") {
    const unknown = state.retry === "send" || state.retry === "check";
    return (
      <>
        <Header title="회원 탈퇴" back="/my" />
        <div className="px-5 pt-16 text-center">
          <h1 className="text-xl font-bold">{unknown ? "탈퇴 결과를 확인하지 못했어요" : "탈퇴하지 못했어요"}</h1>
          <p role="alert" className="mt-2 text-sm text-red-800">{state.message}</p>
          {state.retry === "pin" && <Link href={PIN_HREF} className="btn btn-primary mt-6">간편비밀번호 다시 입력</Link>}
          {state.retry === "send" && <button type="button" onClick={resend} className="btn btn-primary mt-6">다시 시도</button>}
          {state.retry === "check" && <button type="button" onClick={recheck} className="btn btn-primary mt-6">다시 확인</button>}
          {!unknown && <Link href="/my/withdraw" className="btn btn-outline mt-3">정산 내용 다시 확인</Link>}
        </div>
      </>
    );
  }
  const r = state.res;
  return (
    <>
      <Header title="회원 탈퇴" />
      <div className="space-y-4 px-5 pt-10">
        <h1 className="text-center text-xl font-bold">탈퇴가 완료되었어요</h1>
        {r ? (
          <section className="card space-y-2 text-sm">
            <div className="flex justify-between"><span>모의계좌로 환급</span><b>{won(r.refundedAmount)}</b></div>
            <div className="flex justify-between"><span>소멸된 적립 예정 캐시백{r.canceledEarningCount > 0 && ` (${r.canceledEarningCount}건)`}</span><b>{won(r.canceledEarningAmount)}</b></div>
          </section>
        ) : (
          <p className="note text-center">처리 결과를 바로 받지 못했지만 탈퇴는 끝났어요.</p>
        )}
        <LogoutLink href="/login" className="btn btn-primary">로그인 화면으로</LogoutLink>
      </div>
    </>
  );
}
