"use client";

import { useState } from "react";
import ConfirmButton from "@/components/ConfirmButton";
import { getSettings, resetSetting, updateSetting, type OperatorSetting, type SettingKey } from "@/lib/adminApi";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

// 설정마다 확인 창에 알리는 영향. 이미 기록된 적립·충전은 어느 설정이든 바뀌지 않는다
const IMPACT: Record<SettingKey, { apply: string; lowered?: (next: number) => string }> = {
  CASHBACK_USER_MONTHLY_LIMIT: {
    apply: "이후 결제부터 적용하고 이미 기록된 적립은 그대로예요.",
    lowered: (next) => `이번 달에 이미 ${won(next)} 이상 적립한 이용자는 이번 달에 더 적립되지 않아요.`,
  },
  WALLET_HOLDING_LIMIT: { apply: "이후 충전부터 적용하고 이미 충전한 금액은 그대로예요." },
  WALLET_MONTHLY_CHARGE_LIMIT: { apply: "이후 충전부터 적용하고 이미 충전한 금액은 그대로예요." },
};

const impactText = (setting: OperatorSetting, next: number) => {
  const impact = IMPACT[setting.key];
  return [impact?.apply, next < setting.value ? impact?.lowered?.(next) : undefined].filter(Boolean).join(" ");
};

const rangeText = (s: OperatorSetting) => (s.hardMax == null ? `${won(s.minValue)} 이상` : `${won(s.minValue)} ~ ${won(s.hardMax)}`);

function SettingCard({ setting, onSaved }: { setting: OperatorSetting; onSaved: (message: string) => void }) {
  const [draft, setDraft] = useState<string | null>(null); // 손대기 전에는 현재 값을 보여 준다
  const title = setting.description.replace("(원)", "");
  const input = draft ?? String(setting.value);
  const next = Number(input);
  const error =
    input === "" ? "값을 입력해 주세요"
    : !Number.isSafeInteger(next) || next < setting.minValue || (setting.hardMax != null && next > setting.hardMax) ? `${rangeText(setting)}의 정수로 입력해 주세요`
    : "";
  const changed = !error && next !== setting.value;
  const overridden = setting.source === "OPERATOR";

  const save = async () => {
    await updateSetting(setting.key, next);
    setDraft(null);
    onSaved(`${title}를 ${won(next)}으로 바꿨어요`);
  };
  const reset = async () => {
    await resetSetting(setting.key);
    setDraft(null);
    onSaved(`${title}를 기본값 ${won(setting.defaultValue)}으로 되돌렸어요`);
  };

  return (
    <section className="card space-y-3 !p-5" aria-label={title}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-bold">{title}</h2>
        <span className={`badge ${overridden ? "badge-info" : "badge-ok"}`}>{overridden ? "운영자 설정" : "기본값"}</span>
      </div>
      <div className="text-2xl font-extrabold tabular-nums">{won(setting.value)}</div>
      <dl className="grid gap-x-6 gap-y-1 text-[13px] sm:grid-cols-3">
        <div className="flex justify-between gap-2 sm:block"><dt className="text-slate-500">기본값</dt><dd className="font-medium tabular-nums">{won(setting.defaultValue)}</dd></div>
        <div className="flex justify-between gap-2 sm:block"><dt className="text-slate-500">허용 범위</dt><dd className="font-medium tabular-nums">{rangeText(setting)}</dd></div>
        <div className="flex justify-between gap-2 sm:block"><dt className="text-slate-500">마지막 변경</dt><dd className="font-medium tabular-nums">{setting.updatedAt ? setting.updatedAt.slice(0, 16).replace("T", " ") : "-"}</dd></div>
      </dl>
      <div className="flex flex-wrap items-end gap-3 border-t border-slate-100 pt-3">
        <label className="block">
          <span className="label">새 값(원)</span>
          <input className="field !w-48 text-right tabular-nums" inputMode="numeric" value={input} onChange={(e) => setDraft(e.target.value.replace(/[^0-9]/g, ""))} aria-invalid={!!error} />
        </label>
        {changed ? (
          <ConfirmButton label="저장" message={`${won(setting.value)} → ${won(next)}. ${impactText(setting, next)} 바꿀까요?`} onConfirm={save} className="btn btn-primary !w-auto px-6" />
        ) : (
          <button type="button" disabled className="btn btn-primary !w-auto px-6 disabled:opacity-40">저장</button>
        )}
        {overridden && (
          <ConfirmButton label="기본값으로 되돌리기" message={`${won(setting.value)} → ${won(setting.defaultValue)}(기본값). ${impactText(setting, setting.defaultValue)} 되돌릴까요?`} onConfirm={reset} className="btn btn-outline !w-auto px-4" />
        )}
      </div>
      {!error && changed && <p className="note tabular-nums">입력한 값: {won(next)}</p>}
      {error && <p role="alert" className="text-[13px] font-medium text-red-600">{error}</p>}
    </section>
  );
}

// 운영자의 한도 설정: 월 적립 한도, 지갑 보유·월 충전 한도
export default function AdminLimitsPage() {
  const load = useLoad(() => getSettings(), []);
  const [saved, setSaved] = useState("");
  const onSaved = (message: string) => {
    setSaved(message);
    setTimeout(() => setSaved(""), 3000);
    load.reload();
  };

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <h1 className="text-2xl font-extrabold">한도 설정</h1>
      {saved && <p role="status" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white">{saved}</p>}
      {load.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{load.error}</p>}
      {!load.data && load.loading && <p role="status" className="note">한도 설정을 불러오는 중…</p>}
      {load.data?.map((s) => <SettingCard key={s.key} setting={s} onSaved={onSaved} />)}
    </div>
  );
}
