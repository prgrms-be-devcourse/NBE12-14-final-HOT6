"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Countdown from "@/components/Countdown";
import Header from "@/components/Header";
import SignBoard from "@/components/SignBoard";
import { ICONS } from "@/components/icons";
import { ApiError } from "@/lib/api";
import { computePreview, REASON_TEXT, SCENARIOS, type Scenario } from "@/lib/cashbackPreview";
import { loadCtx, saveCtx } from "@/lib/devContext";
import { scanQr, secondsUntil } from "@/lib/endpoints";
import { LIMITS } from "@/lib/limits";
import { won } from "@/lib/format";
import { newPending, savePending } from "@/lib/pendingPayment";
import type { QrScanResponse } from "@/lib/types";

type Load = { kind: "idle" } | { kind: "loading" } | { kind: "error"; message: string } | { kind: "ok"; qr: QrScanResponse };

export default function ScanPage() {
  const router = useRouter();
  const [walletId, setWalletId] = useState(1);
  const [token, setToken] = useState("");
  const [input, setInput] = useState("");
  const [load, setLoad] = useState<Load>({ kind: "idle" });
  const [expired, setExpired] = useState(false);
  const [scenario, setScenario] = useState<Scenario>("normal");
  const [cashbackInput, setCashbackInput] = useState(""); // 비우면 0 (캐시백을 쓰지 않고 데브머니로만 결제)

  const lookup = (t: string) => {
    const v = t.trim();
    if (!v) return;
    setToken(v);
    setExpired(false);
    setLoad({ kind: "loading" });
    scanQr(v)
      .then((qr) => {
        setLoad({ kind: "ok", qr });
        saveCtx({ qrToken: v });
      })
      .catch((e) => setLoad({ kind: "error", message: e instanceof ApiError ? e.message : "QR 정보를 불러오지 못했어요" }));
  };

  useEffect(() => {
    const c = loadCtx();
    const fromUrl = new URLSearchParams(window.location.search).get("token") ?? "";
    const t = fromUrl || c.qrToken || "";
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 브라우저 저장소 값은 마운트 후에 읽는다
    setWalletId(c.walletId);
    setInput(t);
    if (t) lookup(t);
  }, []);

  const qr = load.kind === "ok" ? load.qr : null;
  const amount = qr?.amount ?? 0;
  // 캐시백 사용 금액: 0 ~ 결제금액. 나머지는 데브머니로 낸다. 적립은 데브머니로 낸 금액 기준이다
  const cashbackUsed = cashbackInput.trim() === "" ? 0 : Number(cashbackInput);
  const cashbackOk = Number.isInteger(cashbackUsed) && cashbackUsed >= 0 && cashbackUsed <= amount;
  const devMoney = cashbackOk ? amount - cashbackUsed : amount;
  const p = computePreview(devMoney, qr && !qr.cashbackEligible ? "notEligible" : scenario);
  const earning = p.reason === "NONE" && devMoney > 0;
  const overLimit = amount > LIMITS.perPayment;
  const valid = !!qr && !expired && walletId > 0 && amount > 0 && !overLimit && cashbackOk;

  const go = () => {
    if (!qr) return;
    savePending(newPending({ walletId, qrToken: token, cashbackUsed }));
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
            {p.upcoming && (
              <div className="flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-[13px] text-amber-900">
                <span aria-hidden className="mt-0.5 shrink-0">{ICONS.trendUp}</span>
                <span><b>정책 변경 예고</b> · {p.upcoming.from}부터 적립 요율이 {p.rate.toFixed(1)}% → {p.upcoming.rate.toFixed(1)}%로 바뀌어요</span>
              </div>
            )}
            {p.paused && (
              <div role="status" className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-2.5 text-[13px] font-medium text-red-700">적립 중단 중이에요. 결제는 그대로 진행돼요.</div>
            )}

            <section className="card">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="font-semibold">{qr.merchantName}</div>
                  <span className={`badge mt-1 ${p.eligible ? "badge-ok" : "badge-warn"}`}>{p.eligible ? "적립 가능 가맹점" : "적립 제외 가맹점"}</span>
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
                  <button type="button" onClick={() => setCashbackInput(String(amount))} className="btn btn-outline btn-sm !w-auto shrink-0">전액</button>
                  <button type="button" onClick={() => setCashbackInput("")} className="btn btn-outline btn-sm !w-auto shrink-0">지우기</button>
                </div>
              </label>
              {!cashbackOk && <p role="alert" className="mt-2 text-[13px] font-medium text-red-600">캐시백 사용 금액은 결제금액({won(amount)}) 이하로 입력해 주세요</p>}
              <p className="note mt-2">나머지 {won(devMoney)}은 데브머니로 결제돼요</p>
            </section>

            <SignBoard
              label="결제 미리보기"
              rows={[
                { label: "결제금액", value: won(amount) },
                { label: "데브머니 사용", value: won(devMoney) },
                { label: "캐시백 사용", value: won(cashbackOk ? cashbackUsed : 0) },
                earning
                  ? { label: `예상 캐시백 (${p.rate.toFixed(1)}%)`, value: `+${p.expected.toLocaleString("ko-KR")}원`, tone: "pending" }
                  : { label: "이번 결제는 적립 없음", value: "0원" },
                { label: "월 적립 한도 잔여", value: won(p.monthlyRemaining - (earning ? p.expected : 0)) },
              ]}
            />
            <section className="card">
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-bold text-slate-700">캐시백 미리보기</span>
                <span className="badge">예상</span>
              </div>
              {!earning && <p className="mt-1 text-[13px] text-slate-600">{devMoney === 0 && p.reason === "NONE" ? "캐시백으로만 결제하면 적립되지 않아요." : REASON_TEXT[p.reason]}</p>}
              <dl className="mt-2 space-y-1.5 text-[13px]">
                <div className="flex justify-between"><dt className="text-slate-500">적용 요율 (접수 시각 기준)</dt><dd className="font-bold tabular-nums">{p.rate.toFixed(1)}%</dd></div>
                {earning && <div className="flex justify-between"><dt className="text-slate-500">계산 근거</dt><dd className="font-medium tabular-nums">{devMoney.toLocaleString("ko-KR")} × {p.rate.toFixed(1)}% = {p.expected.toLocaleString("ko-KR")}원</dd></div>}
                <div className="flex justify-between"><dt className="text-slate-500">월 적립 한도 잔여</dt><dd className="font-medium tabular-nums">{won(p.monthlyRemaining)}{earning && ` → ${won(p.monthlyRemaining - p.expected)}`}</dd></div>
              </dl>
              <p className="note mt-2">예상 금액이며 소수점 이하는 버려요. 결제 접수 시각의 요율로 확정돼요.</p>
            </section>

            {expired && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">QR 유효시간이 지났어요. 가맹점에 새 QR을 요청해 주세요.</p>}
            {overLimit && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">결제 한도({won(LIMITS.perPayment)})를 넘어 결제할 수 없어요</p>}
            <button type="button" onClick={go} disabled={!valid} className="btn btn-primary disabled:opacity-40">결제하기 (간편비밀번호)</button>

            {qr.cashbackEligible && (
              <details className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
                <summary className="cursor-pointer font-bold text-slate-700">적립 상황 바꿔 보기</summary>
                <label className="mt-3 block">
                  <span className="label">적립 상황</span>
                  <select className="field" value={scenario} onChange={(e) => setScenario(e.target.value as Scenario)}>
                    {SCENARIOS.filter((s) => s.value !== "notEligible").map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                  </select>
                </label>
              </details>
            )}
          </>
        )}
      </div>
    </>
  );
}
