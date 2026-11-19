"use client";

import { useState } from "react";
import ConfirmButton from "@/components/ConfirmButton";
import Field from "@/components/Field";
import { budgetRows } from "@/lib/mock";

export default function AdminPolicyPage() {
  const [saved, setSaved] = useState("");
  const [rate, setRate] = useState("2.0");
  const [from, setFrom] = useState("2026-12-01");
  const [to, setTo] = useState("2026-12-31");
  const [notify, setNotify] = useState(true);
  const r = Number(rate);
  const policyError =
    !rate.trim() || Number.isNaN(r) || r <= 0 || r > 100 ? "요율은 0보다 크고 100 이하인 숫자로 입력해 주세요"
    : !from || !to ? "적용 시작일과 종료일을 모두 입력해 주세요"
    : from > to ? "종료일은 시작일 이후여야 해요"
    : "";
  const flash = (m: string) => { setSaved(m); setTimeout(() => setSaved(""), 2000); };
  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-semibold">정책 · 예산 관리</h1>
      {saved && <p role="status" className="rounded-lg bg-blue-600 px-4 py-2 text-sm text-white">{saved}</p>}
      <div className="grid gap-4 lg:grid-cols-2">
        <section className="card space-y-3"><h2 className="font-semibold">정책 관리</h2>
          <label className="block"><span className="label">캐시백 요율 (%)</span><input className="field" inputMode="decimal" value={rate} onChange={(e) => setRate(e.target.value)} /></label>
          <label className="block"><span className="label">적용 시작일</span><input className="field" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="block"><span className="label">적용 종료일</span><input className="field" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="h-4 w-4 accent-blue-600" checked={notify} onChange={(e) => setNotify(e.target.checked)} /> 정책 변경 예고 알림 발송</label>
          {policyError && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{policyError}</p>}
          <button type="button" disabled={!!policyError} onClick={() => flash(`요율 ${r}% (${from} ~ ${to}) 정책이 저장되었습니다${notify ? " · 변경 예고 알림 발송" : ""}`)} className="btn btn-primary disabled:opacity-40">저장</button>
          <p className="note">※ 정책 관리의 변경이력은 B 범위에서 제외 (이력 표시는 D 대시보드)</p></section>
        <section className="card space-y-3"><h2 className="font-semibold">운영 제어</h2>
          <button type="button" onClick={() => flash("공지 후 중단이 예약되었습니다")} className="btn btn-outline">공지 후 중단</button>
          <ConfirmButton label="긴급 중단" message="적립이 즉시 중단돼요. 진행할까요?" doneLabel="긴급 중단됨" className="btn btn-danger" />
          <p className="note">대비금 기준: 남은 예산 ≤ 1,000,000원</p>
          <div className="card-gray !p-3 text-sm"><div className="font-semibold">적립 지급 배치</div><p className="mt-1">마지막 실행 11.17 02:00</p><p>성공 120건 · 실패 0건</p><p className="note mt-1">• 건별 트랜잭션, 음수 상계 · 캐시백 내역·요율을 C에게 전달 (전달 방식은 더 논의 필요)</p></div></section>
      </div>
      <section className="card overflow-x-auto space-y-3"><h2 className="font-semibold">연/월 예산 관리</h2>
        <div className="max-w-xs"><Field label="연 예산 배정" defaultValue="10,000,000원" /></div>
        <table className="table"><thead><tr><th>월</th><th>배정</th><th>집행</th><th>예약</th><th>남음</th></tr></thead>
          <tbody>{budgetRows.map((r) => <tr key={r.month}><td>{r.month}</td><td>{r.assigned}</td><td>{r.spent}</td><td>{r.reserved}</td><td>{r.left}</td></tr>)}</tbody></table>
        <div className="flex gap-2"><button type="button" onClick={() => flash("배정이 저장되었습니다")} className="btn btn-primary btn-sm !w-auto">배정 저장</button><button type="button" onClick={() => flash("월 → 다음 월 예산 이월이 실행되었습니다")} className="btn btn-outline btn-sm !w-auto">예산 이월</button></div>
        <p className="note">• 예약 · 차감 · 복원은 결제 시 자동 반영 • 이월: 월 → 다음 월</p></section>
    </div>
  );
}
