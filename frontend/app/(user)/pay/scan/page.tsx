"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Countdown from "@/components/Countdown";
import Header from "@/components/Header";
import SignBoard from "@/components/SignBoard";
import { ICONS } from "@/components/icons";
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
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 브라우저 저장소 값은 마운트 후에 읽는다
    setF((x) => ({ ...x, walletId: c.walletId, merchantId: c.merchantId, qrId: c.qrId }));
    setShop(c.merchantName ?? "");
    setAddr(c.merchantAddress ?? "");
  }, []);
  const setAmount = (e: React.ChangeEvent<HTMLInputElement>) =>
    setF((x) => ({ ...x, amount: Number(e.target.value.replace(/\D/g, "")) || 0 }));

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
      <Header title="QR 결제" back="/wallet" />
      <div className="space-y-3 px-5 pb-5">
        <div className="relative flex h-36 items-center justify-center overflow-hidden rounded-xl bg-slate-900 text-[13px] font-medium text-white/80" role="img" aria-label="QR 스캔 화면">
          <svg className="absolute inset-0 h-full w-full" viewBox="0 0 320 144" fill="none" stroke="white" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
            <path d="M32 54V38a8 8 0 0 1 8-8h16M288 54V38a8 8 0 0 0-8-8h-16M32 90v16a8 8 0 0 0 8 8h16M288 90v16a8 8 0 0 1-8 8h-16" />
          </svg>
          QR 코드를 사각형 안에 맞춰 주세요
        </div>

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
              <div className="font-semibold">{shop || `가맹점 #${f.merchantId}`}</div>
              <span className={`badge mt-1 ${p.eligible ? "badge-ok" : "badge-warn"}`}>{p.eligible ? "적립 가능 가맹점" : "적립 제외 가맹점"}</span>
            </div>
            <span className="note pt-0.5">QR 유효시간 <Countdown seconds={161} className="font-semibold text-slate-900" /></span>
          </div>
          {addr && <p className="note mt-2">{addr}</p>}
          <div className="mt-3 flex items-baseline justify-between border-t border-slate-100 pt-3">
            <span className="note">결제금액</span>
            <span className="amt-out text-[32px] font-extrabold tabular-nums tracking-tight">{won(f.amount)}</span>
          </div>
        </section>

        <SignBoard
          label="결제 미리보기"
          rows={[
            { label: "결제금액", value: won(f.amount) },
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
          {!earning && <p className="mt-1 text-[13px] text-slate-600">{REASON_TEXT[p.reason]}</p>}
          <dl className="mt-2 space-y-1.5 text-[13px]">
            <div className="flex justify-between"><dt className="text-slate-500">적용 요율 (접수 시각 기준)</dt><dd className="font-bold tabular-nums">{p.rate.toFixed(1)}%</dd></div>
            {earning && <div className="flex justify-between"><dt className="text-slate-500">계산 근거</dt><dd className="font-medium tabular-nums">{f.amount.toLocaleString("ko-KR")} × {p.rate.toFixed(1)}% = {p.expected.toLocaleString("ko-KR")}원</dd></div>}
            <div className="flex justify-between"><dt className="text-slate-500">월 적립 한도 잔여</dt><dd className="font-medium tabular-nums">{won(p.monthlyRemaining)}{earning && ` → ${won(p.monthlyRemaining - p.expected)}`}</dd></div>
          </dl>
          <p className="note mt-2">예상 금액이며 소수점 이하는 버려요. 결제 접수 시각의 요율로 확정돼요.</p>
        </section>

        {overLimit && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">결제 한도({won(LIMITS.perPayment)})를 넘어 결제할 수 없어요</p>}
        <button type="button" onClick={go} disabled={!valid} className="btn btn-primary disabled:opacity-40">결제하기 (간편비밀번호)</button>

        <details className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm">
          <summary className="cursor-pointer font-bold text-slate-700">결제 금액과 적립 상황 바꿔 보기</summary>
          <div className="mt-3 space-y-3">
            <label className="block">
              <span className="label">결제 금액</span>
              <input className="field text-right text-lg font-bold tabular-nums" inputMode="numeric" value={f.amount.toLocaleString("ko-KR")} onChange={setAmount} />
            </label>
            <label className="block">
              <span className="label">적립 상황</span>
              <select className="field" value={scenario} onChange={(e) => setScenario(e.target.value as Scenario)}>
                {SCENARIOS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
              </select>
            </label>
          </div>
        </details>

      </div>
    </>
  );
}
