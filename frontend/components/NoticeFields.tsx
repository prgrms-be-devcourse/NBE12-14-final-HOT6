"use client";

import { NOTICE_CONTENT_MAX, NOTICE_TITLE_MAX } from "@/lib/cashbackApi";

export type NoticeDraft = { title: string; content: string; showOnHome: boolean; homeUntil: string };

type Props = {
  name: string; // 화면 낭독기가 읽는 묶음 이름
  value: NoticeDraft;
  onChange: (patch: Partial<NoticeDraft>) => void;
  minHomeUntil: string;
};

// 공지 입력 양식(제목, 본문, 홈 팝업). 한도 변경과 공지 수정이 함께 쓴다
export default function NoticeFields({ name, value, onChange, minHomeUntil }: Props) {
  return (
    <fieldset className="space-y-2.5 rounded-lg bg-slate-50 p-3">
      <legend className="sr-only">{name} 공지</legend>
      <label className="block">
        <span className="label">공지 제목</span>
        <input className="field" maxLength={NOTICE_TITLE_MAX} value={value.title} onChange={(e) => onChange({ title: e.target.value })} />
      </label>
      <label className="block">
        <span className="label">공지 본문 ({value.content.length}/{NOTICE_CONTENT_MAX})</span>
        <textarea className="field min-h-24" maxLength={NOTICE_CONTENT_MAX} value={value.content} onChange={(e) => onChange({ content: e.target.value })} />
      </label>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <label className="flex items-center gap-2 text-[13px] font-medium text-slate-700">
          <input type="checkbox" checked={value.showOnHome} onChange={(e) => onChange({ showOnHome: e.target.checked })} />
          홈 팝업에 띄우기
        </label>
        {value.showOnHome && (
          <label className="flex items-center gap-2 text-[13px] text-slate-600">
            마지막 날
            <input type="date" className="field !w-44 !py-1.5" min={minHomeUntil} value={value.homeUntil} onChange={(e) => onChange({ homeUntil: e.target.value })} />
          </label>
        )}
      </div>
    </fieldset>
  );
}
