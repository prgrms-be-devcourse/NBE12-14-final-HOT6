"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import Countdown from "@/components/Countdown";
import Header from "@/components/Header";
import SignBoard from "@/components/SignBoard";
import { ICONS } from "@/components/icons";
import { ApiError } from "@/lib/api";
import { getCashbackPreview, getMonthlyLimit, getUpcomingPolicyChange, pctText, REASON_TEXT, REASON_TONE, REASON_TONE_CLASS } from "@/lib/cashbackApi";
import { loadCtx, saveCtx } from "@/lib/devContext";
import { getMyWallet, getQrLimit, scanQr, secondsUntil } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { newPending, savePending } from "@/lib/pendingPayment";
import { useLoad } from "@/lib/useLoad";
import type { QrScanResponse, WalletResponse } from "@/lib/types";

type Load = { kind: "idle" } | { kind: "loading" } | { kind: "error"; message: string } | { kind: "ok"; qr: QrScanResponse };

export default function ScanPage() {
  const router = useRouter();
  const [wallet, setWallet] = useState<WalletResponse | null>(null);
  const [walletError, setWalletError] = useState("");
  const [token, setToken] = useState("");
  const [input, setInput] = useState("");
  const [load, setLoad] = useState<Load>({ kind: "idle" });
  const [expired, setExpired] = useState(false);
  const [cashbackInput, setCashbackInput] = useState(""); // 비우면 0 (캐시백을 쓰지 않고 데브머니로만 결제)
  const lastToken = useRef(""); // 마지막으로 조회한 QR (바뀌었는지 비교한다)

  const lookup = (t: string, restored = false) => {
    const v = t.trim();
    if (!v) return;
    // 다른 QR 이면 이전 QR 에서 입력한 캐시백을 지운다 (남아 있으면 새 결제금액을 넘어 결제 버튼이 막힌다)
    if (v !== lastToken.current) setCashbackInput("");
    lastToken.current = v;
    setToken(v);
    setExpired(false);
    setLoad({ kind: "loading" });
    scanQr(v)
      .then((qr) => {
        setLoad({ kind: "ok", qr });
        saveCtx({ qrToken: v });
      })
      .catch((e) => {
        // 쓸 수 없는 QR(이미 사용·만료 등)은 저장해 두지 않는다. 이전에 저장된 번호를 되살렸다가 실패한 거면 조용히 비운다
        saveCtx({ qrToken: undefined });
        if (restored) {
          lastToken.current = "";
          setToken("");
          setInput("");
          setLoad({ kind: "idle" });
          return;
        }
        setLoad({ kind: "error", message: e instanceof ApiError ? e.message : "QR 정보를 불러오지 못했어요" });
      });
  };

  useEffect(() => {
    const c = loadCtx();
    const fromUrl = new URLSearchParams(window.location.search).get("token") ?? "";
    const t = fromUrl || c.qrToken || "";
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 브라우저 저장소 값은 마운트 후에 읽는다
    setInput(t);
    if (t) lookup(t, !fromUrl);
  }, []);

  // 얼마를 쓸 수 있는지 미리 보여 주려고 내 지갑을 읽는다
  useEffect(() => {
    let alive = true;
    getMyWallet()
      .then((w) => {
        if (alive) setWallet(w);
      })
      .catch((e) => {
        if (alive) setWalletError(e instanceof ApiError ? e.message : "지갑 정보를 불러오지 못했어요");
      });
    return () => {
      alive = false;
    };
  }, []);

  const qr = load.kind === "ok" ? load.qr : null;
  const amount = qr?.amount ?? 0;
  // 캐시백 사용 금액: 0 ~ 결제금액. 나머지는 데브머니로 낸다. 적립은 데브머니로 낸 금액 기준이다
  const cashbackUsed = cashbackInput.trim() === "" ? 0 : Number(cashbackInput);
  // 캐시백은 결제금액과 지갑에서 쓸 수 있는 캐시백 중 작은 값까지만 쓸 수 있다
  const cashbackMax = wallet ? Math.min(amount, wallet.availableCashback) : amount;
  const cashbackOk = Number.isInteger(cashbackUsed) && cashbackUsed >= 0 && cashbackUsed <= cashbackMax;
  const devMoney = cashbackOk ? amount - cashbackUsed : amount;
  const devMoneyShort = wallet ? Math.max(0, devMoney - wallet.devMoney) : 0;

  // 결제 전 미리보기: 데브머니로 낼 금액이 바뀔 때마다 서버에서 다시 계산한다 (예상값)
  const previewKey = qr && devMoney > 0 ? `${qr.merchantId}:${devMoney}` : "";
  const previewLoad = useLoad(
    () => (previewKey ? getCashbackPreview(qr!.merchantId, devMoney) : Promise.resolve(null)),
    [previewKey],
  );
  const upcomingLoad = useLoad(() => getUpcomingPolicyChange(), []);
  const p = previewLoad.data;
  const upcoming = upcomingLoad.data;
  const eligible = qr?.cashbackEligible ?? true;
  const reason = !eligible ? "NOT_ELIGIBLE" : (p?.exclusionReason ?? "NONE");
  const earning = !!p && p.earnable && p.expectedAmount > 0 && devMoney > 0;
  // 안내 문구의 색 (적립됨 초록, 대상 아님·데브머니 없음·1원 미만 회색, 월 한도 초과 주황, 예산 소진·적립 중단 빨강)
  // (적립 중단은 위쪽 빨강 안내가 따로 알리므로, 여기서는 사유만 본다. 서버가 중단 달에 적립이 안 되면 사유를 BUDGET_EXHAUSTED 로 준다)
  const tone = earning ? "ok" : devMoney === 0 || previewLoad.loading ? "muted" : REASON_TONE[reason];
  const rate = p?.appliedRate ?? 0;
  const monthlyRemaining = p?.monthlyLimitRemaining ?? 0;
  // 이번 결제까지 반영한 월 한도 사용 비율(%). 70% 미만 초록, 90% 미만 노랑, 그 이상 빨강
  // 월 적립 한도는 운영자가 정한 서버 값이다. 못 불러오면 사용 비율은 0으로 둔다
  const monthlyLimit = useLoad(() => getMonthlyLimit(), []).data?.limit ?? 0;
  const usedPct = p && monthlyLimit > 0 ? Math.min(100, Math.max(0, ((monthlyLimit - (monthlyRemaining - (earning ? p.expectedAmount : 0))) / monthlyLimit) * 100)) : 0;
  const usedTone = usedPct >= 90 ? { bar: "bg-red-500", text: "text-red-700" } : usedPct >= 70 ? { bar: "bg-amber-400", text: "text-amber-700" } : { bar: "bg-emerald-500", text: "text-emerald-700" };
  // 한도는 서버가 정한다. 아직 못 불러왔으면 검사하지 않고, 넘기면 서버가 거절한다
  const maxAmount = useLoad(() => getQrLimit(), []).data?.maxAmount;
  const overLimit = maxAmount != null && amount > maxAmount;
  const valid = !!qr && !expired && !!wallet && devMoneyShort === 0 && amount > 0 && !overLimit && cashbackOk;

  const go = () => {
    if (!qr) return;
    savePending(newPending({ qrToken: token, cashbackUsed }));
    router.push("/pin?next=/pay/receipt");
  };

  return (
    <>
      <Header title="QR 결제" back="/wallet" />
      <div className="space-y-3 px-5 pb-5">
        <div className="relative flex h-36 items-center justify-center overflow-hidden rounded-xl bg-slate-900 text-[13px] font-medium text-white/80" role="img" aria-label="QR 스캔 화면">
          <svg className="absolute inset-0 h-full w-full" viewBox="0 0 320 144" fill="none" stroke="white" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
            <path d="M32 54V38a8 8 0 0 1 8-8h16M288 54V38a8 8 0 0 0-8-8h-16M32 90v16a8 8 0 0 0 8 8h16M288 90v16a8 8 0 0 1-8 8h-16" />
          </svg>
          QR 코드를 사각형 안에 맞춰 주세요
        </div>

        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            lookup(input);
          }}
        >
          <input className="field font-mono text-sm" value={input} onChange={(e) => setInput(e.target.value)} placeholder="QR 코드 번호 직접 입력" aria-label="QR 코드 번호" />
          <button type="submit" disabled={!input.trim() || load.kind === "loading"} className="btn btn-outline btn-sm !w-auto shrink-0 disabled:opacity-40">확인</button>
        </form>

        {load.kind === "loading" && <p role="status" className="note text-center">QR 정보를 불러오는 중이에요…</p>}
        {load.kind === "error" && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{load.message}</p>}
        {load.kind === "idle" && <p className="note text-center">가맹점의 QR을 스캔하거나 코드 번호를 입력해 주세요.</p>}

        {qr && (
          <>
            {upcoming && (
              <div className="flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-[13px] text-amber-900">
                <span aria-hidden className="mt-0.5 shrink-0">{ICONS.trendUp}</span>
                <span><b>정책 변경 예고</b> · {upcoming.content}{upcoming.rate != null && upcoming.previousRate != null && ` (${pctText(upcoming.previousRate)} → ${pctText(upcoming.rate)})`}</span>
              </div>
            )}
            {p?.paused && (
              <div role="status" className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-2.5 text-[13px] font-medium text-red-700">적립 중단 중이에요. 결제는 그대로 진행돼요.</div>
            )}

            <section className="card">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="font-semibold">{qr.merchantName}</div>
                  <span className={`badge mt-1 ${eligible ? "badge-ok" : "badge-warn"}`}>{eligible ? "적립 가능 가맹점" : "적립 제외 가맹점"}</span>
                </div>
                <span className="note pt-0.5">
                  QR 유효시간{" "}
                  {expired ? <b className="text-red-600">만료</b> : <Countdown key={token} seconds={secondsUntil(qr.expiresAt)} onExpire={() => setExpired(true)} className="font-semibold text-slate-900" />}
                </span>
              </div>
              <div className="mt-3 flex items-baseline justify-between border-t border-slate-100 pt-3">
                <span className="note">결제금액</span>
                <span className="amt-out text-[32px] font-extrabold tabular-nums tracking-tight">{won(amount)}</span>
              </div>
            </section>

            <section className="card">
              <span className="text-[13px] font-bold text-slate-700">내 지갑</span>
              {wallet ? (
                <dl className="mt-2 space-y-1.5 text-[13px]">
                  <div className="flex justify-between"><dt className="text-slate-500">데브머니</dt><dd className="font-bold tabular-nums">{won(wallet.devMoney)}</dd></div>
                  <div className="flex justify-between"><dt className="text-slate-500">결제 가능 캐시백</dt><dd className="font-bold tabular-nums">{won(wallet.availableCashback)}</dd></div>
                </dl>
              ) : walletError ? (
                <p role="alert" className="mt-2 text-[13px] font-medium text-red-600">{walletError}</p>
              ) : (
                <p role="status" className="note mt-2">지갑 정보를 불러오는 중이에요…</p>
              )}
              {wallet && wallet.cashbackBalance < 0 && <p className="note mt-2">캐시백 잔액이 {wallet.cashbackBalance.toLocaleString("ko-KR")}원이라 지금은 캐시백을 쓸 수 없어요</p>}
            </section>

            <section className="card">
              <label className="block">
                <span className="label">캐시백 사용 (비우면 사용 안 함)</span>
                <div className="flex gap-2">
                  <input
                    className="field tabular-nums"
                    inputMode="numeric"
                    value={cashbackInput}
                    onChange={(e) => setCashbackInput(e.target.value.replace(/[^0-9]/g, ""))}
                    placeholder="0"
                    aria-invalid={!cashbackOk}
                  />
                  <button type="button" onClick={() => setCashbackInput(String(cashbackMax))} className="btn btn-outline btn-sm !w-auto shrink-0">전액</button>
                  <button type="button" onClick={() => setCashbackInput("")} className="btn btn-outline btn-sm !w-auto shrink-0">지우기</button>
                </div>
              </label>
              {!cashbackOk && (
                <p role="alert" className="mt-2 text-[13px] font-medium text-red-600">
                  {cashbackMax === 0
                    ? "지금 쓸 수 있는 캐시백이 없어요. 캐시백 사용 금액을 비워 주세요"
                    : `캐시백은 ${won(cashbackMax)} 이하로 입력해 주세요 (${wallet && wallet.availableCashback < amount ? "쓸 수 있는 캐시백까지만 사용할 수 있어요" : "결제금액까지만 사용할 수 있어요"})`}
                </p>
              )}
              <p className="note mt-2">나머지 {won(devMoney)}은 데브머니로 결제돼요</p>
            </section>

            <SignBoard
              label="결제 미리보기"
              rows={[
                { label: "결제금액", value: won(amount) },
                { label: "데브머니 사용", value: won(devMoney) },
                { label: "캐시백 사용", value: won(cashbackOk ? cashbackUsed : 0) },
                earning
                  ? { label: `예상 캐시백 (${pctText(rate)})`, value: `+${p!.expectedAmount.toLocaleString("ko-KR")}원`, tone: "pending" }
                  : { label: "이번 결제는 적립 없음", value: "0원" },
              ]}
            />
            <section className="card">
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-bold text-slate-700">캐시백 미리보기</span>
                <span className="badge">예상</span>
              </div>
              {previewLoad.error && <p role="alert" className="mt-1 text-[13px] text-red-600">{previewLoad.error}</p>}
              <div className="mt-3 divide-y divide-slate-100 text-[13px]">
                {!previewLoad.error && (
                  <div className="flex items-center justify-between gap-3 pb-2.5">
                    <span className="text-slate-500">적립 여부</span>
                    <span role="status" className={`rounded-lg border px-2.5 py-1 text-right text-[13px] font-semibold ${REASON_TONE_CLASS[tone]}`}>
                      {earning
                        ? REASON_TEXT.NONE
                        : devMoney === 0 ? "캐시백으로만 결제하면 적립되지 않아요." : previewLoad.loading ? "계산 중이에요…" : REASON_TEXT[reason]}
                    </span>
                  </div>
                )}
                <div className="flex items-center justify-between gap-3 py-2.5">
                  <span className="text-slate-500">적용 요율 (접수 시각 기준)</span>
                  <span className="text-[18px] font-bold tabular-nums text-blue-700">{pctText(rate)}</span>
                </div>
                <div className="pt-2.5">
                  <div className="mb-1.5 flex items-center justify-between gap-3">
                    <span className="text-slate-500">월 적립 한도 잔여</span>
                    <span className={`font-semibold tabular-nums ${usedTone.text}`}>{p ? `${won(monthlyRemaining)}${earning ? ` → ${won(monthlyRemaining - p.expectedAmount)}` : ""}` : "-"}</span>
                  </div>
                  <div className="h-2 overflow-hidden rounded bg-slate-100" role="img" aria-label="이번 달 적립 한도 사용 비율">
                    <div className={`h-full rounded ${usedTone.bar}`} style={{ width: `${usedPct}%` }} />
                  </div>
                </div>
              </div>
              <p className="note mt-2">예상 금액이며 소수점 이하는 버려요. 결제 접수 시각의 요율로 확정돼요.</p>
            </section>

            {expired && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">QR 유효시간이 지났어요. 가맹점에 새 QR을 요청해 주세요.</p>}
            {overLimit && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">결제 한도({won(maxAmount ?? 0)})를 넘어 결제할 수 없어요</p>}
            {devMoneyShort > 0 && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">데브머니가 {won(devMoneyShort)} 부족해요. 충전하거나 캐시백을 더 사용해 주세요</p>}
            <button type="button" onClick={go} disabled={!valid} className="btn btn-primary disabled:opacity-40">결제하기 (간편비밀번호)</button>

          </>
        )}
      </div>
    </>
  );
}
