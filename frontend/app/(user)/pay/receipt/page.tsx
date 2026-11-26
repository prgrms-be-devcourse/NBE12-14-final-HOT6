"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import Header from "@/components/Header";
import SignBoard from "@/components/SignBoard";
import { ICONS } from "@/components/icons";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { getPaymentByKey, pay } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { paymentErrorText } from "@/lib/paymentErrors";
import { saveCtx } from "@/lib/devContext";
import { clearPending, loadPending, type PendingPayment } from "@/lib/pendingPayment";
import { dropPin, peekPin } from "@/lib/pinStore";
import { getPayoutRules, REASON_TEXT, pctText, type ExclusionCode } from "@/lib/cashbackApi";
import { useCashbackPaused } from "@/lib/cashbackStatus";
import { useLoad } from "@/lib/useLoad";
import type { PaymentResponse } from "@/lib/types";

type State =
  | { kind: "loading" }
  | { kind: "none" }
  | { kind: "polling" } // 응답을 못 받아 같은 멱등키로 결과를 재조회하는 중
  | { kind: "timeout" } // 재조회로도 확인하지 못함
  | { kind: "error"; message: string; retryPin?: boolean } // retryPin: 간편비밀번호가 틀려 결제가 시작되지 않음
  | { kind: "ok"; res: PaymentResponse };

const STATUS_LABEL: Record<PaymentResponse["status"], string> = {
  PROCESSING: "처리 중", COMPLETED: "결제 완료", FAILED: "결제 실패", CANCELED: "결제 취소",
};

// 재조회 간격: 2 → 4 → 8 → 16초 (합 30초). 그래도 모르면 안내 문구를 보여 준다
const POLL_DELAYS_MS = [2000, 4000, 8000, 16000];
const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
const fmt = (iso?: string | null) => (iso ? iso.slice(0, 16).replace("T", " ") : "-");

function ReceiptBody() {
  const router = useRouter();
  const [state, setState] = useState<State>({ kind: "loading" });
  // 마지막으로 처리한 결제의 멱등키. 화면 상태가 보존된 채 다시 들어와도 키가 다르면 새 결제로 처리한다
  const handledKey = useRef<string | null>(null);
  // 결과(성공·실패·시간 초과)를 화면에 아직 못 보여 준 상태. 화면을 벗어나 재조회가 멈췄다면 돌아왔을 때 이어서 확인한다
  const inFlight = useRef(false);
  const alive = useRef(true);
  const pending = useRef<PendingPayment | null>(null);
  const payoutDelayDays = useLoad(() => getPayoutRules(), []).data?.payoutDelayDays; // 지급 예정일 안내에 쓰는 서버 설정값
  // 예산 사유로 제외된 결제만, 지금 적립이 중단 상태인지 받아 안내 문구를 고른다 (서버는 소진과 중단을 같은 사유로 남긴다)
  const paused = useCashbackPaused(state.kind === "ok" && state.res.cashbackExclusionReason === "BUDGET_EXHAUSTED");

  useEffect(() => {
    alive.current = true;
    return () => { alive.current = false; };
  }, []);

  // 결과가 정해지면(성공·실패) 메모리에 둔 간편비밀번호를 지운다. 결과를 모르는 동안은 다시 결제할 수 있게 둔다
  const show = (s: State) => {
    if (s.kind === "ok" || s.kind === "error") dropPin();
    // 결제가 끝났으면 쓴 QR 번호를 지운다. 다시 QR 결제 화면에 들어왔을 때 이전 번호가 남아 있지 않게 한다 (실패는 같은 QR로 다시 시도할 수 있게 둔다)
    if (s.kind === "ok" && (s.res.status === "COMPLETED" || s.res.status === "CANCELED")) saveCtx({ qrToken: undefined });
    if (alive.current) {
      setState(s);
      inFlight.current = s.kind === "loading" || s.kind === "polling";
    }
  };

  // 같은 멱등키로 결과를 다시 조회한다. 서버는 새로 처리하지 않고 저장된 결과만 돌려준다
  const poll = async (p: PendingPayment) => {
    show({ kind: "polling" });
    for (const delay of POLL_DELAYS_MS) {
      await sleep(delay);
      if (!alive.current) return;
      try {
        const res = await getPaymentByKey(p.key);
        if (res.status !== "PROCESSING") return show({ kind: "ok", res });
      } catch {
        /* 404(PAY-003, 서버가 받은 적 없음)나 연결 오류는 계속 시도한다 */
      }
    }
    show({ kind: "timeout" });
  };

  // 결제 요청. 다시 보내도 같은 멱등키라 중복 결제되지 않는다
  const submit = async (p: PendingPayment) => {
    // 간편비밀번호는 저장소에 남기지 않는다. 새로고침 등으로 사라졌으면 다시 입력받는다
    const pin = peekPin();
    if (!pin) return router.replace("/pin?next=/pay/receipt");
    show({ kind: "loading" });
    try {
      const res = await pay({ ...p.request, pin }, p.key);
      if (res.status === "PROCESSING") return poll(p);
      show({ kind: "ok", res });
    } catch (e) {
      // 응답을 못 받았거나(연결 오류, 5xx) 이전 요청이 아직 처리 중이면 재조회로 결과를 확인한다
      if (e instanceof ApiError && (e.status === 0 || e.status >= 500 || e.code === "COM-004")) return poll(p);
      if (e instanceof ApiError && e.code === "AUTH-006") return router.replace("/locked"); // 5회 실패로 잠김
      show({
        kind: "error",
        message: e instanceof ApiError ? paymentErrorText(e.code, e.message) : paymentErrorText(),
        retryPin: e instanceof ApiError && e.code === "AUTH-005",
      });
    }
  };

  // 화면에 들어올 때마다 대기 중인 결제를 읽는다 (cacheComponents 로 이전 화면 상태가 남아 있을 수 있다)
  useEffect(() => {
    const p = loadPending();
    pending.current = p;
    if (!p) {
      handledKey.current = null;
      // eslint-disable-next-line react-hooks/set-state-in-effect -- 대기 중인 결제는 마운트 후 세션 저장소에서 읽는다
      setState({ kind: "none" });
      return;
    }
    if (handledKey.current === p.key) {
      // 이미 처리한 결제: 결과를 그대로 보여 준다. 결과를 못 받은 채 화면을 벗어났다면 같은 키로 재조회를 이어 간다
      if (inFlight.current) void poll(p);
      return;
    }
    handledKey.current = p.key;
    inFlight.current = true;
    setState({ kind: "loading" });
    void submit(p);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 마운트 시 한 번만 실행한다
  }, []);

  if (state.kind === "loading") return <p className="px-5 pt-20 text-center text-sm text-zinc-500" role="status">결제 처리 중…</p>;
  if (state.kind === "polling")
    return (
      <div className="px-5 pt-20 text-center" role="status">
        <p className="text-sm font-medium text-zinc-700">결제 결과를 확인하고 있어요</p>
        <p className="note mt-2">잠시만 기다려 주세요. 이 화면을 벗어나거나 다시 결제하지 마세요.</p>
      </div>
    );
  if (state.kind === "none")
    return <div className="px-5 pt-20 text-center"><p className="text-sm text-zinc-500">진행 중인 결제가 없습니다</p><Link href="/pay/scan" className="btn btn-primary mt-4">QR 결제로 이동</Link></div>;
  if (state.kind === "timeout")
    return (
      <div className="flex flex-1 flex-col px-5 pt-16">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-amber-100 text-amber-800" aria-hidden>{ICONS.clock}</div>
        <h1 className="mt-3 text-center text-xl font-bold">결제 내역을 확인해 주세요</h1>
        <p role="alert" className="mt-2 text-center text-sm text-zinc-600">결제 결과를 아직 확인하지 못했어요. 결제가 처리됐을 수 있으니 내역을 확인해 주세요.</p>
        <div className="mt-6 flex flex-col gap-2.5">
          <button type="button" className="btn btn-primary" onClick={() => pending.current && void poll(pending.current)}>다시 조회</button>
          {/* 서버가 받은 적 없는 요청(계속 404)이면 같은 키로 다시 결제해도 중복되지 않는다 */}
          <button type="button" className="btn btn-outline" onClick={() => pending.current && void submit(pending.current)}>같은 요청으로 다시 결제</button>
          <Link href="/pay/history" className="btn btn-outline">결제 내역 보기</Link>
        </div>
      </div>
    );
  if (state.kind === "error")
    return (
      <div className="flex flex-1 flex-col px-5 pt-16">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-50 text-red-600" aria-hidden>{ICONS.alert}</div>
        <h1 className="mt-3 text-center text-xl font-bold">{state.retryPin ? "간편비밀번호를 확인해 주세요" : "결제에 실패했습니다"}</h1>
        <p role="alert" className="mt-2 text-center text-sm text-red-800">{state.message}</p>
        <div className="mt-6 flex gap-2.5"><Link href={state.retryPin ? "/pin?next=/pay/receipt" : "/pay/scan"} className="btn btn-outline">{state.retryPin ? "다시 입력" : "다시 시도"}</Link><Link href="/wallet" className="btn btn-primary" onClick={clearPending}>확인</Link></div>
      </div>
    );

  const r = state.res;
  const ok = r.status === "COMPLETED";

  // 서버가 결제를 실패 처리한 경우: failureCode 로 사유를 보여 준다
  if (r.status === "FAILED")
    return (
      <div className="flex flex-1 flex-col px-5 pt-16">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-red-50 text-red-600" aria-hidden>{ICONS.alert}</div>
        <h1 className="mt-3 text-center text-xl font-bold">결제에 실패했습니다</h1>
        <p role="alert" className="mt-2 text-center text-sm text-red-800">{paymentErrorText(r.failureCode, "결제를 처리하지 못했어요. 잠시 후 다시 시도해 주세요")}</p>
        <div className="mt-6 flex gap-2.5"><Link href="/pay/scan" className="btn btn-outline">다시 시도</Link><Link href="/wallet" className="btn btn-primary" onClick={clearPending}>확인</Link></div>
      </div>
    );

  const rows: [string, string][] = [
    ["적용요율", r.appliedRate != null ? pctText(r.appliedRate) : "-"],
    ["지급 예정일", r.cashbackPayableAt ? `${r.cashbackPayableAt.slice(0, 10)}${payoutDelayDays != null ? ` (결제 다음 날부터 ${payoutDelayDays}일 뒤)` : ""}` : "-"],
    ["결제 시각", fmt(r.completedAt)],
    ...(r.status === "CANCELED" ? ([["취소 시각", fmt(r.canceledAt)]] as [string, string][]) : []),
  ];
  const reason = r.cashbackExclusionReason as ExclusionCode | null;
  const excluded = !!reason && reason !== "NONE";

  return (
    <div className="flex flex-1 flex-col px-5 pt-10">
      <div className={`mx-auto flex h-14 w-14 items-center justify-center rounded-full ${ok ? "bg-emerald-50 text-emerald-600" : "bg-amber-100 text-amber-800"}`} aria-hidden>{ok ? ICONS.check : ICONS.clock}</div>
      <h1 className="mt-3 text-center text-xl font-bold">{STATUS_LABEL[r.status]}</h1>
      <p className="amt-out mt-1 text-center text-[36px] font-extrabold tabular-nums tracking-tight">{won(r.amount)}</p>
      <section className="card mt-6">
        <div className="mb-3 flex items-center justify-between gap-2">
          <span className="font-bold">확정 영수증</span>
          <span className="rounded-md bg-slate-100 px-2.5 py-1 text-[13px] font-bold tabular-nums text-slate-800">결제번호 PAY-{String(r.paymentId).padStart(4, "0")}</span>
        </div>
        <SignBoard
          label="결제 분기"
          rows={[
            { label: "데브머니 사용", value: won(r.walletUsedAmount) },
            { label: "캐시백 사용", value: won(r.cashbackUsedAmount) },
            { label: "적립 예정 캐시백", value: r.cashbackEarnedAmount ? `+${won(r.cashbackEarnedAmount)}` : "없음", tone: "pending" },
          ]}
        />
        <dl className="mt-1">{rows.map(([k, v]) => <div key={k} className="flex justify-between border-t border-zinc-100 py-2 text-sm"><dt className="text-zinc-500">{k}</dt><dd className="font-medium tabular-nums">{v}</dd></div>)}</dl>
        {excluded && <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-[13px] text-amber-900">적립 제외 · {reason === "BUDGET_EXHAUSTED" && paused ? "적립이 중단되어 이번 결제는 적립되지 않았어요" : (REASON_TEXT[reason as ExclusionCode] ?? reason)}</p>}
        <p className="note mt-2">소수점 이하는 버림 · 접수 시각 기준 요율로 확정</p>
      </section>
      {/* 결제 취소는 가맹점주 기능이라 사용자 화면에는 두지 않는다 */}
      <div className="mt-5 flex gap-2.5">
        <Link href="/wallet" className="btn btn-primary" onClick={clearPending}>확인</Link>
      </div>
    </div>
  );
}

export default function ReceiptPage() {
  return (
    <>
      <Header title="결제 결과" />
      <ReceiptBody />
    </>
  );
}
