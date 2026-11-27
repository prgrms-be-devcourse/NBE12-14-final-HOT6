"use client";

import { useEffect, useState } from "react";
import ConfirmButton from "@/components/ConfirmButton";
import NoticeFields from "@/components/NoticeFields";
import { BasisGrid, type BasisValues } from "@/components/PolicyBasisDetail";
import { ApiError } from "@/lib/api";
import { getForecast, getMonthlyBudget, getReservation, REASON_MAX, reduceRate, reviseReduction, thisYearMonth, type Forecast, type MonthlyBudget, type RateReservation } from "@/lib/adminApi";
import { NOTICE_CONTENT_MAX, NOTICE_TITLE_MAX } from "@/lib/cashbackApi";
import { monthEnd, ymd } from "@/lib/dates";
import { useLoad } from "@/lib/useLoad";

// 서버가 근거로 남기는 항목과 같게 맞춘다
const toBasis = (b: MonthlyBudget, f: Forecast): BasisValues => ({
  allocatedAmount: b.allocatedAmount, paidAmount: b.paidAmount, reservedAmount: b.reservedAmount,
  remainingAmount: b.remainingAmount, contingencyAmount: b.contingencyAmount, dailyDemand: f.assumptions.baseDailyDemand,
  fastExhaustionDate: f.fast.exhaustionDate, midExhaustionDate: f.mid.exhaustionDate, slowExhaustionDate: f.slow.exhaustionDate,
  noticeLineDate: f.mid.noticeLineDate,
});

const koDate = (date: string) => `${Number(date.slice(5, 7))}월 ${Number(date.slice(8, 10))}일`;
const DEFAULT_TITLE = "적립률이 바뀌어요";
// 요율(%)과 적용 시각에 맞춘 공지 본문 초안
const draftContent = (rate: string, from: string) =>
  `${from ? `${koDate(from)}부터` : "적용하는 날부터"} 적립률이 ${rate ? `${Number(rate)}%` : "새 요율"}로 바뀌어요. 이미 접수된 결제는 약속한 금액 그대로 적립돼요`;

type Props = {
  policyId: number | null; // null 이면 새 인하, 값이 있으면 그 예약의 수정
  initialRate: string; // 새 인하에서 미리 넣을 요율(%)
  maxRate: number | null; // 이 값보다 낮아야 한다(%). 모르면 서버가 확인한다
  onClose: () => void;
  onSaved: (message: string) => void;
};

// 적립률 인하 창. 요율, 적용 시각, 사유, 공지를 입력하고, 아직 적용되지 않은 예약은 같은 창에서 고친다
export default function RateReductionModal(props: Props) {
  const { policyId, onClose } = props;
  const load = useLoad<RateReservation | null>(() => (policyId == null ? Promise.resolve(null) : getReservation(policyId)), [policyId ?? 0]);
  // 입력을 시작한 뒤에는 바깥을 눌러도 닫지 않는다
  const [dirty, setDirty] = useState(false);
  const title = policyId == null ? "적립률 인하" : "예약한 인하 수정";
  // 다른 예약을 열면 새로 불러오기 전까지 이전 예약의 값을 보여 주지 않는다
  const ready = policyId == null || load.data?.policyId === policyId;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4" onClick={dirty ? undefined : onClose}>
      <div role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()} className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl bg-white p-5">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-lg font-bold">{title}</h2>
          <button type="button" onClick={onClose} className="btn btn-outline btn-sm !w-auto">닫기</button>
        </div>
        {load.error ? (
          <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{load.error}</p>
        ) : !ready ? (
          <p role="status" className="note mt-3">불러오는 중…</p>
        ) : (
          <ReductionForm {...props} saved={load.data ?? null} onDirty={() => setDirty(true)} />
        )}
      </div>
    </div>
  );
}

function ReductionForm({ policyId, initialRate, maxRate, onSaved, saved, onDirty }: Props & { saved: RateReservation | null; onDirty: () => void }) {
  const savedRate = saved ? String(Math.round(saved.rate * 1000) / 10) : "";
  const savedFrom = saved ? saved.effectiveAt.slice(0, 16) : "";
  const old = saved?.notice ?? null;
  const [rate, setRate] = useState(saved ? savedRate : initialRate);
  const [from, setFrom] = useState(savedFrom);
  const [reason, setReason] = useState(saved?.reason ?? "");
  // 공지는 직접 고치기 전(null)까지 요율과 적용 시각에 맞춘 초안을 따른다. 저장된 공지가 초안과 다르면 그대로 둔다
  const [titleInput, setTitle] = useState<string | null>(old && old.title !== DEFAULT_TITLE ? old.title : null);
  const [contentInput, setContent] = useState<string | null>(old && old.content !== draftContent(savedRate, savedFrom) ? old.content : null);
  const [showOnHome, setShowOnHome] = useState(old ? old.showOnHome : true);
  const [homeUntilInput, setHomeUntil] = useState<string | null>(old?.homeUntil && old.homeUntil !== monthEnd(savedFrom) ? old.homeUntil : null);

  // 저장할 때 서버가 남기는 것과 같은 이번 달 예산과 예측(최근 7일 기준). 이번 달 예산이 없으면 null
  const { year, month } = thisYearMonth();
  const basis = useLoad(
    () => Promise.all([getMonthlyBudget(year, month), getForecast()]).catch((e: unknown) => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
    [year, month],
  );

  const today = ymd(new Date());
  const effective = from ? from.slice(0, 10) : today;
  const minHomeUntil = effective > today ? effective : today;
  const noticeTitle = titleInput ?? DEFAULT_TITLE;
  const content = contentInput ?? draftContent(rate, from);
  const homeUntil = homeUntilInput ?? monthEnd(minHomeUntil);

  const r = Number(rate);
  // 새 인하의 상한은 이번 달 요율이라, 다음 달 이후 날짜에는 서버가 확인한다
  const limit = policyId == null && from && Number(from.slice(5, 7)) !== month ? null : maxRate;
  const rateError =
    !rate.trim() || Number.isNaN(r) || r <= 0 || r > 100 ? "요율은 0보다 크고 100 이하인 숫자로 입력해 주세요"
    : limit != null && r >= limit ? `요율은 직전 요율(${limit}%)보다 낮아야 해요`
    : "";
  const error =
    rateError
    || (!from ? "적용 시작 일시를 입력해 주세요"
    : saved && from.slice(0, 7) !== savedFrom.slice(0, 7) ? "적용 시각은 같은 달 안에서만 바꿀 수 있어요"
    : !reason.trim() ? "인하 사유를 입력해 주세요"
    : !noticeTitle.trim() ? "공지 제목을 입력해 주세요"
    : !content.trim() ? "공지 본문을 입력해 주세요"
    : noticeTitle.length > NOTICE_TITLE_MAX || content.length > NOTICE_CONTENT_MAX ? "공지가 너무 길어요"
    : showOnHome && (!homeUntil || homeUntil < minHomeUntil) ? "홈에 띄우는 마지막 날은 오늘과 적용일 이후여야 해요"
    : "");
  const changed =
    !saved || rate !== savedRate || from !== savedFrom || reason.trim() !== (saved.reason ?? "")
    || noticeTitle.trim() !== old?.title || content.trim() !== old?.content || showOnHome !== old?.showOnHome
    || (showOnHome && homeUntil !== old?.homeUntil);
  // 손대지 않은 빈 양식에는 오류를 보여 주지 않는다
  const touched = rate !== (saved ? savedRate : initialRate) || from !== savedFrom || reason !== (saved?.reason ?? "") || titleInput !== null || contentInput !== null;
  const valueChanged = saved != null && (rate !== savedRate || from !== savedFrom);
  const input = <T,>(set: (v: T) => void) => (v: T) => { set(v); onDirty(); };

  const label = saved ? "공지 고치고 저장" : "공지하고 저장";
  const message = `${from && `${koDate(from)} ${from.slice(11, 16)}부터 ${r}%.`} ${saved ? "예약과 게시한 공지를 고칠까요?" : "공지는 지금 게시돼요. 인하할까요?"}`;
  const save = async () => {
    const body = {
      rate: r / 100,
      // 시각을 바꾸지 않았으면 저장된 값을 그대로 보낸다
      effectiveAt: saved && from === savedFrom ? saved.effectiveAt : `${from}:00`,
      reason: reason.trim(),
      notice: { title: noticeTitle.trim(), content: content.trim(), showOnHome, homeUntil: showOnHome ? homeUntil : null },
    };
    // 실패하면 확인 버튼 옆에 서버 메시지가 보인다
    if (policyId == null) await reduceRate(body);
    else await reviseReduction(policyId, body);
    onSaved(policyId == null ? `요율 ${r}% (${from.replace("T", " ")}부터) 인하를 저장하고 공지를 게시했어요` : "예약한 인하를 수정했어요");
  };

  return (
    <>
      <div className="mt-3 space-y-3 overflow-auto">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block">
            <span className="label">새 요율(%){limit != null && <span className="note"> 직전 {limit}%</span>}</span>
            <input className="field text-right tabular-nums" inputMode="decimal" value={rate} onChange={(e) => input(setRate)(e.target.value.replace(/[^\d.]/g, ""))} />
          </label>
          <label className="block">
            <span className="label">적용 시작 일시</span>
            <input className="field" type="datetime-local" value={from} onChange={(e) => input(setFrom)(e.target.value)} />
          </label>
        </div>
        <label className="block">
          <span className="label">인하 사유 <span className="note tabular-nums">{reason.length}/{REASON_MAX}</span></span>
          <textarea className="field min-h-20" maxLength={REASON_MAX} placeholder="예: 최근 결제가 늘어 이번 달 예산이 일찍 소진될 것으로 보여요" value={reason} onChange={(e) => input(setReason)(e.target.value)} />
          <span className="note">운영자만 보는 기록이에요. 지금의 예산과 소진 예측이 함께 변경 이력에 남아요.</span>
        </label>
        <div className="space-y-3 rounded-lg bg-slate-50 px-3 py-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-bold">저장할 때 기록되는 근거 <span className="note font-normal">최근 7일 수요 기준 · 저장하는 순간의 값이 남아요</span></p>
            <button type="button" onClick={basis.reload} disabled={basis.loading} className="btn btn-outline btn-sm !w-auto disabled:opacity-40">새로고침</button>
          </div>
          {basis.loading ? (
            <p className="note">근거를 불러오는 중…</p>
          ) : basis.error ? (
            <p className="note">{basis.error}</p>
          ) : !basis.data ? (
            <p className="note">이번 달 예산이 없어 직전 요율만 남아요.</p>
          ) : (
            <BasisGrid basis={toBasis(...basis.data)} />
          )}
        </div>
        <h3 className="font-bold">이용자 공지</h3>
        {valueChanged && contentInput !== null && <p className="note text-amber-800">공지 본문은 직접 쓴 것이라 그대로예요. 요율과 날짜가 맞는지 확인해 주세요.</p>}
        <NoticeFields
          name="적립률 인하"
          value={{ title: noticeTitle, content, showOnHome, homeUntil }}
          minHomeUntil={minHomeUntil}
          onChange={(patch) => {
            if (patch.title !== undefined) input(setTitle)(patch.title);
            if (patch.content !== undefined) input(setContent)(patch.content);
            if (patch.showOnHome !== undefined) input(setShowOnHome)(patch.showOnHome);
            if (patch.homeUntil !== undefined) input(setHomeUntil)(patch.homeUntil);
          }}
        />
        {saved && <p className="note">제목이나 본문을 고치면 이용자 화면에 &quot;수정됨&quot;과 날짜가 보여요. 월 인하 횟수는 늘지 않아요.</p>}
      </div>
      <div className="mt-3 space-y-2 border-t border-slate-200 pt-3">
        {touched && error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{error}</p>}
        {!error && changed ? (
          // 값을 바꾸면 확인 창을 처음부터 다시 연다
          <ConfirmButton key={`${rate}-${from}`} label={label} message={message} onConfirm={save} className="btn btn-primary !w-auto px-6" />
        ) : (
          <button type="button" disabled className="btn btn-primary !w-auto px-6 disabled:opacity-40">{label}</button>
        )}
      </div>
    </>
  );
}
