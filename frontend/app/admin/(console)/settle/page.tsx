"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/lib/api";
import { addDays, ymd } from "@/lib/dates";
import { getReconciliations, runDailySettlement, runMonthlySettlement, runReconciliation } from "@/lib/endpoints";
import type { ReconciliationResultResponse, ReconciliationType } from "@/lib/types";

const TYPES: { type: ReconciliationType; label: string; note: string }[] = [
  { type: "ACCOUNT_BALANCE", label: "계좌 잔액", note: "계좌 잔액 = 계좌 원장 합" },
  { type: "DEVMONEY_BALANCE", label: "데브머니 잔액", note: "지갑 데브머니 = 원장 합" },
  { type: "CASHBACK_BALANCE", label: "캐시백 잔액", note: "지갑 캐시백 = 원장 합" },
  { type: "CASHBACK_EARNING", label: "캐시백 적립 건", note: "적립 건 상태 = 원장 기록" },
  { type: "CASHBACK_BUDGET", label: "캐시백 예산", note: "월·연 예산의 확보·지급 = 적립 건 합계" },
  { type: "CHARGE_TRANSFER", label: "충전", note: "충전액 = 계좌 차감 = 지갑 입금" },
  { type: "REFUND_TRANSFER", label: "환불", note: "환불액 = 지갑 차감 = 계좌 입금" },
  { type: "DAILY_NET_SALES", label: "일 순매출", note: "장부 순매출 = 결제 − 취소" },
  { type: "SETTLEMENT_CARRY", label: "확정액·이월", note: "확정액 + 이월 = 순매출 + 전일 이월" },
  { type: "MONTHLY_PAYOUT", label: "월 지급", note: "월 지급액 = 일 정산 합 = 지갑 원장" },
];

type Msg = { ok: boolean; text: string } | null;

const fmtTime = (iso: string) => iso.slice(0, 16).replace("T", " ");
const errText = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback);

export default function AdminSettlePage() {
  const [tab, setTab] = useState<"일 정산" | "월 정산">("일 정산");
  const [date, setDate] = useState("");
  const [month, setMonth] = useState("");
  const [running, setRunning] = useState(false);
  const [runMsg, setRunMsg] = useState<Msg>(null);

  const [reconDate, setReconDate] = useState("");
  const [results, setResults] = useState<ReconciliationResultResponse[]>([]);
  const [reconBusy, setReconBusy] = useState(false);
  const [reconMsg, setReconMsg] = useState<Msg>(null);

  // 최근 일주일의 대사 결과. 서버는 최근 실행 순으로 준다
  const loadResults = useCallback(() => {
    const today = new Date();
    return getReconciliations(ymd(addDays(today, -6)), ymd(today)).then(setResults);
  }, []);

  useEffect(() => {
    loadResults().catch((e) => setReconMsg({ ok: false, text: errText(e, "대사 결과를 불러오지 못했어요") }));
  }, [loadResults]);

  const runSettlement = async () => {
    setRunning(true);
    setRunMsg(null);
    try {
      if (tab === "일 정산") {
        const r = await runDailySettlement(date || undefined);
        setRunMsg({ ok: r.failed === 0, text: `${r.date} 일 정산: 새로 만든 건 ${r.created}, 이미 있어 건너뜀 ${r.skipped}, 실패 ${r.failed}` });
      } else {
        const r = await runMonthlySettlement(month || undefined);
        setRunMsg({ ok: r.failed === 0, text: `${r.month} 월 정산: 새로 만든 건 ${r.created}, 지급 완료 ${r.paid}, 실패 ${r.failed}` });
      }
    } catch (e) {
      setRunMsg({ ok: false, text: errText(e, "정산을 실행하지 못했어요") });
    } finally {
      setRunning(false);
    }
  };

  const runRecon = async () => {
    setReconBusy(true);
    setReconMsg(null);
    try {
      const fresh = await runReconciliation(reconDate || undefined);
      setResults((prev) => [...fresh, ...prev]);
      const bad = fresh.filter((r) => r.status !== "MATCHED").length;
      const missing = TYPES.length - fresh.length;
      if (missing > 0) {
        // 서버가 일부 종류를 돌려주지 않았다. 일치한다고 보이면 안 된다
        setReconMsg({ ok: false, text: `${TYPES.length}종류 중 ${fresh.length}종류만 결과가 왔어요. 나머지 ${missing}종류는 실행되지 않았어요` });
      } else {
        setReconMsg({ ok: bad === 0, text: bad === 0 ? `${fresh.length}종류가 모두 일치해요` : `${fresh.length}종류 중 ${bad}종류가 불일치이거나 오류예요` });
      }
    } catch (e) {
      setReconMsg({ ok: false, text: errText(e, "대사를 실행하지 못했어요") });
    } finally {
      setReconBusy(false);
    }
  };

  // 종류별로 가장 최근에 실행한 결과 하나만 보여 준다
  const latestOf = (type: ReconciliationType) => results.find((r) => r.type === type);

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-semibold">정산 · 원장 대사</h1>

      <section className="card">
        <h2 className="mb-2 font-semibold">일 / 월 정산 실행</h2>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex gap-2" role="tablist">
            {(["일 정산", "월 정산"] as const).map((t) => (
              <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => { setTab(t); setRunMsg(null); }} className={`chip ${tab === t ? "chip-on" : ""}`}>{t}</button>
            ))}
          </div>
          {tab === "일 정산" ? (
            <label className="block"><span className="label">날짜 (비우면 어제)</span><input type="date" className="field" value={date} onChange={(e) => setDate(e.target.value)} /></label>
          ) : (
            <label className="block"><span className="label">월 (비우면 지난달)</span><input type="month" className="field" value={month} onChange={(e) => setMonth(e.target.value)} /></label>
          )}
          <button type="button" onClick={runSettlement} disabled={running} className="btn btn-primary btn-sm !w-auto disabled:opacity-40">{running ? "실행 중…" : "정산 실행"}</button>
        </div>
        {runMsg && <p role={runMsg.ok ? "status" : "alert"} className={`mt-3 rounded-lg px-3 py-2 text-sm font-medium ${runMsg.ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-700"}`}>{runMsg.text}</p>}
        <p className="note mt-2">
          • 일 정산은 어제까지, 월 정산은 지난달까지 실행할 수 있어요 • 이미 만든 정산과 지급은 건너뛰므로 다시 실행해도 중복되지 않아요 • 월 정산은 해당 달의 일 정산이 끝난 뒤에 실행해요
        </p>
      </section>

      <section className="card">
        <h2 className="font-semibold">원장 대사 <span className="note">({TYPES.length}종 · 독립 실행 · 읽기전용)</span></h2>
        <div className="mt-2 flex flex-wrap items-end gap-3">
          <label className="block"><span className="label">대사 날짜 (비우면 어제)</span><input type="date" className="field" value={reconDate} onChange={(e) => setReconDate(e.target.value)} /></label>
          <button type="button" onClick={runRecon} disabled={reconBusy} className="btn btn-primary btn-sm !w-auto disabled:opacity-40">{reconBusy ? "실행 중…" : "대사 실행"}</button>
          <button type="button" onClick={() => loadResults().catch((e) => setReconMsg({ ok: false, text: errText(e, "대사 결과를 불러오지 못했어요") }))} className="btn btn-outline btn-sm !w-auto">결과 새로고침</button>
        </div>
        {reconMsg && <p role={reconMsg.ok ? "status" : "alert"} className={`mt-3 rounded-lg px-3 py-2 text-sm font-medium ${reconMsg.ok ? "bg-emerald-50 text-emerald-800" : "bg-red-50 text-red-700"}`}>{reconMsg.text}</p>}
        <ul className="mt-3 grid gap-2 sm:grid-cols-2">
          {TYPES.map(({ type, label, note }) => {
            const r = latestOf(type);
            return (
              <li key={type} className="rounded-lg border border-zinc-200 px-3 py-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium">{label}</span>
                  {!r && <span className="badge">기록 없음</span>}
                  {r?.status === "MATCHED" && <span className="badge badge-ok">일치</span>}
                  {r?.status === "MISMATCHED" && <span className="badge badge-bad">불일치 {r.mismatchCount}</span>}
                  {r?.status === "ERROR" && <span className="badge badge-bad">오류</span>}
                </div>
                <div className="note mt-0.5">{note}</div>
                {r && <div className="note mt-1">{r.targetDate} 기준 · 검사 {r.checkedCount}건 · {fmtTime(r.executedAt)} 실행</div>}
                {r?.status === "MISMATCHED" && r.mismatchRefs && <div className="mt-1 break-all text-xs text-red-700">{r.mismatchRefs}</div>}
                {r?.status === "ERROR" && r.errorMessage && <div className="mt-1 break-all text-xs text-red-700">{r.errorMessage}</div>}
              </li>
            );
          })}
        </ul>
        <p className="note mt-3">• 잔액 대사(계좌·데브머니·캐시백)는 날짜와 상관없이 실행 시점의 전체를 봐요 • 불일치는 기록만 하고 자동으로 고치지 않아요</p>
      </section>
    </div>
  );
}
