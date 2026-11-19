"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Countdown from "@/components/Countdown";
import Header from "@/components/Header";
import { computePreview, REASON_TEXT, SCENARIOS, type Scenario } from "@/lib/cashbackPreview";
import { loadCtx } from "@/lib/devContext";
import { LIMITS } from "@/lib/limits";
import { won } from "@/lib/format";
import { newPending, savePending } from "@/lib/pendingPayment";

export default function ScanPage() {
  const router = useRouter();
  const [f, setF] = useState({ walletId: 1, merchantId: 1, qrId: 1, amount: 12000 });
  const [scenario, setScenario] = useState<Scenario>("normal");
  const [shop, setShop] = useState("");
  const [addr, setAddr] = useState("");
  useEffect(() => {
    const c = loadCtx();
    setF((x) => ({ ...x, walletId: c.walletId, merchantId: c.merchantId, qrId: c.qrId }));
    setShop(c.merchantName ?? "");
    setAddr(c.merchantAddress ?? "");
  }, []);
  const num = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setF((x) => ({ ...x, [k]: Number(e.target.value.replace(/\D/g, "")) || 0 }));

  const p = computePreview(f.amount, scenario);
  const earning = p.reason === "NONE";
  const go = () => {
    savePending(newPending(f));
    router.push("/pin?next=/pay/receipt");
  };
  const overLimit = f.amount > LIMITS.perPayment;
  const valid = f.walletId > 0 && f.merchantId > 0 && f.qrId > 0 && f.amount > 0 && !overLimit;

  return (
    <>
      <Header title="QR 결제" back="/wallet" right="QR 스캔" />
      <div className="space-y-3 px-5 pb-5">
        <div className="flex h-28 items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50 text-[13px] text-slate-500">카메라 영역 (QR API 연동 전)</div>

        {p.upcoming && (
          <div className="flex gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-2.5 text-[13px] text-amber-900">
            <span aria-hidden>📢</span>
            <span><b>정책 변경 예고</b> · {p.upcoming.from}부터 적립 요율이 {p.rate.toFixed(1)}% → {p.upcoming.rate.toFixed(1)}%로 바뀌어요</span>
          </div>
        )}
        {p.paused && (
          <div role="status" className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-2.5 text-[13px] font-medium text-red-700">적립 중단 중이에요. 결제는 그대로 진행돼요.</div>
        )}

        <section className="card">
          <div className="flex items-start justify-between gap-2">
            <div>
              <div className="font-semibold">{shop || `가맹점 #${f.merchantId}`}</div>
              <span className={`badge mt-1 ${p.eligible ? "badge-ok" : "badge-warn"}`}>{p.eligible ? "적립 가능 가맹점" : "적립 제외 가맹점"}</span>
            </div>
            <span className="note pt-0.5">QR 유효시간 <Countdown seconds={161} className="font-semibold text-slate-900" /></span>
          </div>
          {addr && <p className="note mt-2">{addr}</p>}
          <div className="mt-3 flex items-baseline justify-between border-t border-slate-100 pt-3">
            <span className="note">결제금액</span>
            <span className="amt-out text-2xl font-bold">{won(f.amount)}</span>
          </div>
        </section>

        <section className={`rounded-xl border p-4 ${earning ? "border-emerald-200 bg-emerald-50" : "border-slate-200 bg-slate-50"}`}>
          <div className="flex items-center justify-between">
            <span className={`text-[13px] font-semibold ${earning ? "text-emerald-800" : "text-slate-600"}`}>캐시백 미리보기</span>
            <span className="badge">예상</span>
          </div>
          <div className={`mt-1 text-xl font-bold ${earning ? "amt-cb" : "text-slate-500"}`}>
            {earning ? `예상 캐시백 ${p.expected.toLocaleString("ko-KR")}원` : "이번 결제는 적립되지 않아요"}
          </div>
          {!earning && <p className="mt-1 text-[13px] text-slate-600">{REASON_TEXT[p.reason]}</p>}

          <dl className="mt-3 space-y-1.5 border-t border-black/5 pt-3 text-[13px]">
            <div className="flex justify-between"><dt className="text-slate-500">적용 요율 (접수 시각 기준)</dt><dd className="font-medium">{p.rate.toFixed(1)}%</dd></div>
            {earning && <div className="flex justify-between"><dt className="text-slate-500">계산 근거</dt><dd className="font-medium">{f.amount.toLocaleString("ko-KR")} × {p.rate.toFixed(1)}% = {p.expected.toLocaleString("ko-KR")}원</dd></div>}
            <div className="flex justify-between"><dt className="text-slate-500">월 적립 한도 잔여</dt><dd className="font-medium">{won(p.monthlyRemaining)}{earning && ` → ${won(p.monthlyRemaining - p.expected)}`}</dd></div>
          </dl>
          <p className="note mt-2">예상 금액이며 소수점 이하는 버려요. 결제 접수 시각의 요율로 확정돼요.</p>
        </section>

        {overLimit && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">결제 한도({won(LIMITS.perPayment)})를 넘어 결제할 수 없어요</p>}
        <button type="button" onClick={go} disabled={!valid} className="btn btn-primary disabled:opacity-40">결제하기 (간편비밀번호)</button>

        <p className="note">결제 → 지갑 차감 · 예산 예약 · QR 사용처리</p>

        <details className="card-dash">
          <summary className="cursor-pointer font-semibold">개발용 · 요청 값과 미리보기 상황</summary>
          <div className="mt-3 grid grid-cols-2 gap-2">
            {(["walletId", "merchantId", "qrId", "amount"] as const).map((k) => (
              <label key={k} className="block"><span className="label">{k}</span><input className="field !py-1.5" inputMode="numeric" value={f[k]} onChange={num(k)} /></label>
            ))}
          </div>
          <label className="mt-3 block"><span className="label">미리보기 상황 (미리보기 API 연동 전 데모)</span>
            <select className="field !py-1.5" value={scenario} onChange={(e) => setScenario(e.target.value as Scenario)}>
              {SCENARIOS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
            </select></label>
          <p className="mt-2">결제 요청은 POST /api/payments 로 전송됩니다.</p>
        </details>
      </div>
    </>
  );
}
