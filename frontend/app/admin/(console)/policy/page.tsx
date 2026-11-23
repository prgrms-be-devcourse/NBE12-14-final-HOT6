"use client";

import { useState } from "react";
import BudgetPlanner from "@/components/BudgetPlanner";
import ConfirmButton from "@/components/ConfirmButton";
import RateSimulator from "@/components/RateSimulator";
import { BASE_RATE } from "@/lib/budget";

export default function AdminPolicyPage() {
  const [saved, setSaved] = useState("");
  const [rate, setRate] = useState(String(BASE_RATE));
  const [from, setFrom] = useState("2026-12-01");
  const [to, setTo] = useState("2026-12-31");
  const [notify, setNotify] = useState(true);
  const r = Number(rate);
  const policyError =
    !rate.trim() || Number.isNaN(r) || r <= 0 || r > 100 ? "요율은 0보다 크고 100 이하인 숫자로 입력해 주세요"
    : !from || !to ? "적용 시작일과 종료일을 모두 입력해 주세요"
    : from > to ? "종료일은 시작일 이후여야 해요"
    : from <= "2026-11-30" && r > BASE_RATE ? `이번 달에는 요율을 올릴 수 없어요. 현재 ${BASE_RATE}%보다 낮게 정하거나 다음 달부터 적용해 주세요`
    : "";
  const flash = (m: string) => { setSaved(m); setTimeout(() => setSaved(""), 2000); };

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-extrabold">정책 · 예산 관리</h1>
      {saved && <p role="status" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white">{saved}</p>}

      <section className="card space-y-4 !p-5">
        <h2 className="text-lg font-bold">월 예산</h2>
        <BudgetPlanner onFlash={flash} />
      </section>

      <section className="card !p-5">
        <h2 className="mb-4 text-lg font-bold">캐시백 정책 · 요율 시뮬레이션</h2>
        <RateSimulator rate={rate} onRate={setRate} />
        <div className="mt-5 space-y-3 border-t border-slate-200 pt-5">
          <h3 className="font-bold">정책 저장</h3>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block"><span className="label">적용 시작일</span><input className="field" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
            <label className="block"><span className="label">적용 종료일</span><input className="field" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          </div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-brand-600" checked={notify} onChange={(e) => setNotify(e.target.checked)} /> 정책 변경 예고 알림 발송</label>
          {policyError && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{policyError}</p>}
          <button type="button" disabled={!!policyError} onClick={() => flash(`요율 ${r}% (${from} ~ ${to}) 정책이 저장되었습니다${notify ? " · 변경 예고 알림 발송" : ""}`)} className="btn btn-primary !w-auto px-8 disabled:opacity-40">저장</button>
          <p className="note">달 중에는 요율을 인하만 할 수 있고, 올리려면 다음 달 기본 요율로 정해요.</p>
        </div>
      </section>

      <section className="card space-y-3 !p-5">
        <h2 className="text-lg font-bold">운영 제어</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <button type="button" onClick={() => flash("공지 후 중단이 예약되었습니다")} className="btn btn-outline">공지 후 중단</button>
          <ConfirmButton label="긴급 중단" message="적립이 즉시 중단돼요. 진행할까요?" doneLabel="긴급 중단됨" className="btn btn-danger" />
        </div>
        <p className="note">대비금 기준: 월 배정의 10%, 남은 예산이 대비금의 절반에 닿으면 즉시 중단</p>
        <div className="card-gray !p-3 text-sm"><div className="font-bold">적립 지급 배치</div><p className="mt-1">마지막 실행 11.17 02:00</p><p>성공 120건 · 실패 0건</p></div>
      </section>
    </div>
  );
}
