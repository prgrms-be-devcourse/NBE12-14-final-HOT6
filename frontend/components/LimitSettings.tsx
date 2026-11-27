"use client";

import { useState } from "react";
import ConfirmButton from "@/components/ConfirmButton";
import NoticeFields from "@/components/NoticeFields";
import { cancelSchedule, getSettings, resetSetting, scheduleSetting, updateSetting, type OperatorSetting, type SettingKey, type SettingNotice } from "@/lib/adminApi";
import { LIMIT_NAME, NOTICE_CONTENT_MAX, NOTICE_TITLE_MAX } from "@/lib/cashbackApi";
import { addDays, monthEnd, ymd } from "@/lib/dates";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

// 설정마다 확인 창에 알리는 영향. 이미 기록된 적립·충전은 어느 설정이든 바뀌지 않는다
const IMPACT: Record<SettingKey, { apply: string; kept: string; lowered?: (next: number) => string }> = {
  CASHBACK_USER_MONTHLY_LIMIT: {
    apply: "이후 결제부터 적용하고 이미 기록된 적립은 그대로예요.",
    kept: "이미 적립한 캐시백은 그대로예요.",
    lowered: (next) => `이번 달에 이미 ${won(next)} 이상 적립한 이용자는 이번 달에 더 적립되지 않아요.`,
  },
  WALLET_HOLDING_LIMIT: { apply: "이후 충전부터 적용하고 이미 충전한 금액은 그대로예요.", kept: "이미 충전한 금액은 그대로예요." },
  WALLET_MONTHLY_CHARGE_LIMIT: { apply: "이후 충전부터 적용하고 이미 충전한 금액은 그대로예요.", kept: "이미 충전한 금액은 그대로예요." },
};

// 모든 변경은 공지와 함께 한다
type Method = "NOW" | "SCHEDULE";
const METHODS: { value: Method; label: string; save: string }[] = [
  { value: "NOW", label: "바로 적용", save: "공지하고 저장" },
  { value: "SCHEDULE", label: "날짜 예약", save: "공지하고 예약" },
];

const rangeText = (s: OperatorSetting) => (s.hardMax == null ? `${won(s.minValue)} 이상` : `${won(s.minValue)} ~ ${won(s.hardMax)}`);
const koDate = (date: string) => `${Number(date.slice(5, 7))}월 ${Number(date.slice(8, 10))}일`;

function SettingCard({ setting, onSaved }: { setting: OperatorSetting; onSaved: (message: string) => void }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<string | null>(null); // 손대기 전에는 현재 값을 보여 준다
  const [method, setMethod] = useState<Method>("NOW");
  const [date, setDate] = useState("");
  // 공지 문구와 홈 종료일은 직접 고치기 전까지 입력한 값에 맞춘 초안을 보여 준다
  const [titleInput, setTitle] = useState<string | null>(null);
  const [contentInput, setContent] = useState<string | null>(null);
  const [showOnHome, setShowOnHome] = useState(true);
  const [homeUntilInput, setHomeUntil] = useState<string | null>(null);

  const name = LIMIT_NAME[setting.key] ?? setting.description.replace("(원)", "");
  const scheduled = setting.scheduled ?? null;
  const input = draft ?? String(setting.value);
  const next = Number(input);
  const today = ymd(new Date());
  const tomorrow = ymd(addDays(new Date(), 1));
  const overridden = setting.source === "OPERATOR";
  const toDefault = next === setting.defaultValue; // 기본값을 넣으면 운영자 설정을 지우는 변경이 된다
  const nextText = toDefault ? `${won(next)}(기본값)` : won(next);

  const effective = method === "SCHEDULE" && date ? date : today;
  const when = method === "SCHEDULE" ? (date ? `${koDate(date)}부터` : "예약한 날부터") : "오늘부터";
  const title = titleInput ?? `${name}가 바뀌어요`;
  const content = contentInput ?? `${when} ${name}가 ${input ? won(next) : "새 값"}으로 바뀌어요.\n${IMPACT[setting.key]?.kept ?? ""}`;
  const homeUntil = homeUntilInput ?? monthEnd(effective);

  const valueError =
    input === "" ? "값을 입력해 주세요"
    : !Number.isSafeInteger(next) || next < setting.minValue || (setting.hardMax != null && next > setting.hardMax) ? `${rangeText(setting)}의 정수로 입력해 주세요`
    : "";
  const changed = !valueError && next !== setting.value;
  const formError =
    method === "SCHEDULE" && (!date || date < tomorrow) ? "적용일은 내일 이후로 골라 주세요"
    : !title.trim() || title.length > NOTICE_TITLE_MAX ? `공지 제목은 ${NOTICE_TITLE_MAX}자까지 입력해 주세요`
    : !content.trim() || content.length > NOTICE_CONTENT_MAX ? `공지 본문은 ${NOTICE_CONTENT_MAX}자까지 입력해 주세요`
    : showOnHome && (!homeUntil || homeUntil < effective) ? `홈에 띄우는 마지막 날은 ${method === "SCHEDULE" ? "적용일" : "오늘"} 이후여야 해요`
    : "";

  const impact = IMPACT[setting.key];
  const lowered = next < setting.value ? impact?.lowered?.(next) : undefined;
  const message =
    method === "SCHEDULE"
      ? `${date && koDate(date)} 00:00부터 ${nextText}. 공지는 지금 등록돼요. ${lowered ?? ""} 예약할까요?`
      : `${won(setting.value)} → ${nextText}. ${impact?.apply ?? ""} ${lowered ?? ""} 공지가 함께 등록돼요. 바꿀까요?`;

  const clear = () => {
    setEditing(false); setDraft(null); setMethod("NOW"); setDate(""); setTitle(null); setContent(null); setShowOnHome(true); setHomeUntil(null);
  };
  const save = async () => {
    const notice: SettingNotice = { title: title.trim(), content: content.trim(), showOnHome, homeUntil: showOnHome ? homeUntil : null };
    if (method === "SCHEDULE") await scheduleSetting(setting.key, { value: toDefault ? null : next, effectiveDate: date, notice });
    else if (toDefault && overridden) await resetSetting(setting.key, notice);
    else await updateSetting(setting.key, next, notice);
    clear();
    onSaved(method === "SCHEDULE" ? `${name} 변경을 ${koDate(date)}로 예약했어요` : `${name}를 ${nextText}으로 바꿨어요`);
  };
  const cancel = async () => {
    await cancelSchedule(setting.key);
    onSaved(`${name} 예약을 취소했어요`);
  };
  const saveLabel = METHODS.find((m) => m.value === method)!.save;

  return (
    <div className="space-y-3 rounded-xl border border-slate-200 p-4" role="group" aria-label={name}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-bold">{name}</h3>
        <span className={`badge ${overridden ? "badge-info" : "badge-ok"}`}>{overridden ? "운영자 설정" : "기본값"}</span>
      </div>
      <div className="text-2xl font-extrabold tabular-nums">{won(setting.value)}</div>
      <dl className="grid gap-x-6 gap-y-1 text-[13px] sm:grid-cols-3">
        <div className="flex justify-between gap-2 sm:block"><dt className="text-slate-500">기본값</dt><dd className="font-medium tabular-nums">{won(setting.defaultValue)}</dd></div>
        <div className="flex justify-between gap-2 sm:block"><dt className="text-slate-500">허용 범위</dt><dd className="font-medium tabular-nums">{rangeText(setting)}</dd></div>
        <div className="flex justify-between gap-2 sm:block"><dt className="text-slate-500">마지막 변경</dt><dd className="font-medium tabular-nums">{setting.updatedAt ? setting.updatedAt.slice(0, 16).replace("T", " ") : "-"}</dd></div>
      </dl>

      {scheduled && (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2">
          <p className="text-[13px] font-medium text-amber-900">
            <b>예약됨</b> · {koDate(scheduled.effectiveDate)} 00:00부터 <b className="tabular-nums">{won(scheduled.value)}</b>{scheduled.reset && " (기본값)"}
          </p>
          <ConfirmButton label="예약 취소" message="예약을 취소하고 그 공지를 거둘까요?" onConfirm={cancel} className="btn btn-outline btn-sm !w-auto" />
        </div>
      )}

      {/* 예약이 남은 채 바꾸면 예약 공지와 값이 어긋나므로, 취소해야 바꿀 수 있다 */}
      {scheduled ? (
        <p className="note">값을 바꾸거나 새로 예약하려면 지금 예약을 먼저 취소해 주세요.</p>
      ) : !editing ? (
        <button type="button" onClick={() => setEditing(true)} className="btn btn-outline btn-sm !w-auto">변경</button>
      ) : (
      <div className="space-y-3 border-t border-slate-100 pt-3">
        <div className="flex flex-wrap items-end gap-3">
          <label className="block">
            <span className="label">새 값(원)</span>
            <input className="field !w-48 text-right tabular-nums" inputMode="numeric" value={input} onChange={(e) => setDraft(e.target.value.replace(/[^0-9]/g, ""))} aria-invalid={!!valueError} />
          </label>
          {setting.value !== setting.defaultValue && (
            <button type="button" onClick={() => setDraft(String(setting.defaultValue))} className="btn btn-outline btn-sm !w-auto">기본값 넣기</button>
          )}
        </div>
        {valueError && <p role="alert" className="text-[13px] font-medium text-red-600">{valueError}</p>}

        {changed && (
          <>
            <div className="flex flex-wrap gap-2" role="group" aria-label={`${name} 바꾸는 방법`}>
              {METHODS.map((m) => (
                <button key={m.value} type="button" aria-pressed={method === m.value} onClick={() => setMethod(m.value)} className={`chip ${method === m.value ? "chip-on" : ""}`}>
                  {m.label}
                </button>
              ))}
            </div>
            {method === "SCHEDULE" && (
              <label className="block">
                <span className="label">적용일(00:00부터)</span>
                <input type="date" className="field !w-48" min={tomorrow} value={date} onChange={(e) => setDate(e.target.value)} />
              </label>
            )}
            <NoticeFields
                name={name}
                value={{ title, content, showOnHome, homeUntil }}
                minHomeUntil={effective}
                onChange={(patch) => {
                  if (patch.title !== undefined) setTitle(patch.title);
                  if (patch.content !== undefined) setContent(patch.content);
                  if (patch.showOnHome !== undefined) setShowOnHome(patch.showOnHome);
                  if (patch.homeUntil !== undefined) setHomeUntil(patch.homeUntil);
                }}
              />
            {formError && <p role="alert" className="text-[13px] font-medium text-red-600">{formError}</p>}
          </>
        )}

        <div className="flex flex-wrap items-center gap-2">
          {changed && !formError ? (
            // 값이나 방법을 바꾸면 확인 창을 처음부터 다시 연다
            <ConfirmButton key={`${next}-${method}-${date}`} label={saveLabel} message={message} onConfirm={save} className="btn btn-primary !w-auto px-6" />
          ) : (
            <button type="button" disabled className="btn btn-primary !w-auto px-6 disabled:opacity-40">{saveLabel}</button>
          )}
          <button type="button" onClick={clear} className="btn btn-outline !w-auto px-4">닫기</button>
        </div>
      </div>
      )}
    </div>
  );
}

// 운영자의 한도 설정: 월 적립 한도, 지갑 보유·월 충전 한도. 공지와 함께 바로 바꾸거나 날짜를 예약한다
export default function LimitSettings({ onSaved }: { onSaved: (message: string) => void }) {
  const load = useLoad(() => getSettings(), []);
  const saved = (message: string) => {
    onSaved(message);
    load.reload();
  };
  return (
    <>
      {load.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{load.error}</p>}
      {!load.data && load.loading && <p role="status" className="note">한도 설정을 불러오는 중…</p>}
      {load.data?.map((s) => <SettingCard key={s.key} setting={s} onSaved={saved} />)}
    </>
  );
}
