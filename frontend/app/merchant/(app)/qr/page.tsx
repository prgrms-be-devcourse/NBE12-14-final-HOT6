"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import Countdown from "@/components/Countdown";
import Header from "@/components/Header";
import { ApiError } from "@/lib/api";
import { saveCtx } from "@/lib/devContext";
import { createDynamicQr, getQrStatus, secondsUntil } from "@/lib/endpoints";
import { LIMITS } from "@/lib/limits";
import { won } from "@/lib/format";
import type { QrCreateResponse, QrStatus } from "@/lib/types";

const STATUS_LABEL: Record<QrStatus, string> = { WAITING: "대기중", USED: "결제완료", EXPIRED: "만료" };
const POLL_MS = 2000;

export default function MerchantQrPage() {
  const [amount, setAmount] = useState(12000);
  const [qr, setQr] = useState<QrCreateResponse | null>(null);
  const [status, setStatus] = useState<QrStatus | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!qr || status !== "WAITING") return;
    const id = setInterval(() => {
      getQrStatus(qr.token).then((r) => setStatus(r.status)).catch(() => {});
    }, POLL_MS);
    return () => clearInterval(id);
  }, [qr, status]);

  const expire = useCallback(() => setStatus((s) => (s === "WAITING" ? "EXPIRED" : s)), []);

  const create = async () => {
    setError("");
    setBusy(true);
    setCopied(false);
    try {
      const res = await createDynamicQr(amount);
      setQr(res);
      setStatus("WAITING");
      saveCtx({ qrToken: res.token });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "QR을 만들지 못했어요");
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    if (!qr) return;
    try {
      await navigator.clipboard.writeText(qr.token);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const over = amount > LIMITS.perPayment;

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
        {over && <p role="alert" className="text-[13px] font-medium text-red-600">결제 한도({won(LIMITS.perPayment)})를 넘는 금액은 QR을 만들 수 없어요</p>}
        {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-700">{error}</p>}
        <button type="button" onClick={create} disabled={busy || amount === 0 || over} className="btn btn-primary disabled:opacity-40">{busy ? "만드는 중…" : "QR 생성"}</button>

        {qr && status && (
          <section className="card text-center">
            <div className={`mx-auto grid h-44 w-44 grid-cols-7 gap-0.5 rounded-lg border-2 border-brand-600 p-2 ${status === "WAITING" ? "" : "opacity-30"}`} aria-label="결제 QR">
              {Array.from({ length: 49 }, (_, i) => <span key={i} className={((i * 7 + (i >> 2)) % 3 === 0) ? "bg-slate-800" : "bg-slate-100"} />)}
            </div>
            <p className="mt-3 text-lg font-extrabold tabular-nums">{won(qr.amount)}</p>
            <p className="mt-1 text-sm">
              남은 시간{" "}
              {status === "WAITING"
                ? <Countdown key={qr.token} seconds={secondsUntil(qr.expiresAt)} onExpire={expire} className="font-semibold" />
                : <span className="font-semibold">00:00</span>}
            </p>
            <div className="mt-3 flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 text-left">
              <span className="min-w-0 flex-1 truncate font-mono text-xs text-slate-600" title={qr.token}>{qr.token}</span>
              <button type="button" onClick={copy} className="btn btn-outline btn-sm !w-auto shrink-0">{copied ? "복사됨" : "코드 복사"}</button>
            </div>
          </section>
        )}

        <div>
          <span className="label">QR 상태</span>
          <div className="flex gap-2" role="status" aria-live="polite">
            {(Object.keys(STATUS_LABEL) as QrStatus[]).map((s) => (
              <span key={s} className={`chip flex-1 text-center ${status === s ? "chip-on" : ""}`}>{STATUS_LABEL[s]}</span>
            ))}
          </div>
        </div>

        {status === "USED" && qr && (
          <section className="rounded-xl bg-emerald-600 p-4 text-center text-white">
            <div className="text-sm font-medium text-white/85">QR 결제 완료</div>
            <div className="text-2xl font-extrabold tabular-nums">{won(qr.amount)}</div>
            <Link href="/merchant/payments" className="mt-3 inline-block text-sm font-bold underline underline-offset-2">결제 내역 보기</Link>
          </section>
        )}
        {status === "EXPIRED" && <p className="note text-center">QR 유효시간이 지났어요. 새 QR을 만들어 주세요.</p>}
      </div>
    </>
  );
}
