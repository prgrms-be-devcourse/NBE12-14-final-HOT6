"use client";

import { useState } from "react";
import { won } from "@/lib/format";
import { dailySettles } from "@/lib/mock";

const INIT = [1, 2, 3, 4, 5, 6, 7].map((n) => ({ n, result: n === 4 ? "불일치 2" : "일치" }));

export default function AdminSettlePage() {
  const [tab, setTab] = useState<"일 정산" | "월 정산">("일 정산");
  const [recon, setRecon] = useState(INIT);
  const [msg, setMsg] = useState("");
  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-semibold">정산 · 원장 대사</h1>
      <section className="card overflow-x-auto"><h2 className="mb-2 font-semibold">일 / 월 정산</h2><div className="mb-3 flex items-center justify-between">
        <div className="flex gap-2" role="tablist">{(["일 정산", "월 정산"] as const).map((t) => <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => setTab(t)} className={`chip ${tab === t ? "chip-on" : ""}`}>{t}</button>)}</div>
        <button type="button" onClick={() => setMsg(`${tab}이 생성되었습니다 (같은 날짜 중복 생성 방지)`)} className="btn btn-primary btn-sm !w-auto">정산 생성</button></div>
        {msg && <p role="status" className="mb-2 text-sm text-emerald-700">{msg}</p>}
        {tab === "일 정산" ? (
          <table className="table"><thead><tr><th>일자</th><th>순매출</th><th>이월</th><th>상태</th></tr></thead>
            <tbody>{dailySettles.map((d) => <tr key={d.date}><td>{d.date}</td><td className="amt-in font-medium">{won(d.net)}</td><td>{won(d.carry)}</td><td><span className="badge badge-ok">생성됨</span></td></tr>)}</tbody></table>
        ) : (
          <table className="table"><thead><tr><th>월</th><th>순매출</th><th>상태</th></tr></thead>
            <tbody><tr><td>2026.11</td><td>{won(5640000)}</td><td><span className="badge badge-warn">진행중</span></td></tr><tr><td>2026.10</td><td>{won(6120000)}</td><td><span className="badge badge-ok">확정</span></td></tr></tbody></table>
        )}
        <p className="note mt-2">• 순매출 = 결제 − 취소 • 같은 날짜 재실행 시 중복 생성 방지</p></section>
      <section className="card"><h2 className="font-semibold">원장 대사 <span className="note">(7종 · 독립 실행 · 읽기전용)</span></h2>
        <ul className="mt-3 grid gap-2 sm:grid-cols-2">{recon.map((r) => (
          <li key={r.n} className="flex items-center justify-between rounded-lg border border-zinc-200 px-3 py-2">
            <span className="text-sm">대사 {r.n}</span>
            <span className="flex items-center gap-2"><span className={`badge ${r.result === "일치" ? "badge-ok" : "badge-bad"}`}>{r.result}</span>
              <button type="button" onClick={() => setRecon((x) => x.map((y) => (y.n === r.n ? { ...y, result: "일치" } : y)))} className="btn btn-outline btn-sm !w-auto">실행</button></span>
          </li>))}</ul>
        <p className="note mt-3">• 대사 종류 7가지는 D 담당이 정의 • 결제 · 지갑 · 예산 원장을 읽기만 함</p></section>
    </div>
  );
}
