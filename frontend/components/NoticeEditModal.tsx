"use client";

import { useEffect, useState } from "react";
import NoticeChange from "@/components/NoticeChange";
import NoticeFields, { type NoticeDraft } from "@/components/NoticeFields";
import { updateNotice, type AdminNotice } from "@/lib/adminApi";
import { NOTICE_BADGE, NOTICE_LABEL } from "@/lib/cashbackApi";
import { monthEnd, ymd } from "@/lib/dates";

type Props = { item: AdminNotice; onClose: () => void; onSaved: () => void };

// 공지 수정 창. 문구와 홈 팝업만 고치고, 알리는 값과 적용일은 보여 주기만 한다. 거둔 공지는 조회만 된다.
// 바깥을 누르거나 Esc 로 닫는다
export default function NoticeEditModal({ item, onClose, onSaved }: Props) {
  const n = item.notice;
  const today = ymd(new Date());
  // 홈 종료일은 오늘과 적용일 이후여야 한다
  const minHomeUntil = n.effectiveFrom && n.effectiveFrom.slice(0, 10) > today ? n.effectiveFrom.slice(0, 10) : today;
  const [draft, setDraft] = useState<NoticeDraft>({
    title: n.title, content: n.content, showOnHome: item.showOnHome, homeUntil: item.homeUntil ?? monthEnd(minHomeUntil),
  });
  const [saving, setSaving] = useState(false);
  const [apiError, setApiError] = useState("");
  const withdrawn = item.status === "WITHDRAWN";

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const textChanged = draft.title.trim() !== n.title || draft.content.trim() !== n.content;
  const homeChanged = draft.showOnHome !== item.showOnHome || (draft.showOnHome && draft.homeUntil !== item.homeUntil);
  const error =
    !draft.title.trim() ? "공지 제목을 입력해 주세요"
    : !draft.content.trim() ? "공지 본문을 입력해 주세요"
    // 지난 종료일을 그대로 둔 채 문구만 고치는 것은 된다
    : draft.showOnHome && homeChanged && (!draft.homeUntil || draft.homeUntil < minHomeUntil) ? "홈에 띄우는 마지막 날은 오늘과 적용일 이후여야 해요"
    : "";

  const save = async () => {
    setSaving(true);
    setApiError("");
    try {
      await updateNotice(n.noticeId, {
        title: draft.title.trim(), content: draft.content.trim(), showOnHome: draft.showOnHome, homeUntil: draft.showOnHome ? draft.homeUntil : null,
      });
      onSaved();
    } catch (e) {
      setApiError(e instanceof Error ? e.message : "저장하지 못했어요");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-4" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label="공지 상세" onClick={(e) => e.stopPropagation()} className="flex max-h-[85vh] w-full max-w-xl flex-col rounded-2xl bg-white p-5">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-lg font-bold">{withdrawn ? "공지 상세" : "공지 수정"}</h2>
          <button type="button" onClick={onClose} className="btn btn-outline btn-sm !w-auto">닫기</button>
        </div>
        <div className="mt-3 space-y-3 overflow-auto">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`badge ${NOTICE_BADGE[n.type]}`}>{NOTICE_LABEL[n.type]}</span>
            <span className="note">등록 {n.createdAt.slice(0, 16).replace("T", " ")}{n.updatedAt && ` · 수정 ${n.updatedAt.slice(0, 16).replace("T", " ")}`}</span>
          </div>
          <NoticeChange notice={n} />
          {withdrawn ? (
            <>
              <p className="rounded-lg bg-slate-50 px-3 py-2 text-[13px] text-slate-600">예약을 취소해 거둔 공지예요. 이용자에게 보이지 않고 고칠 수 없어요.</p>
              <h3 className="break-words font-bold">{n.title}</h3>
              <p className="whitespace-pre-line break-words text-sm text-slate-700">{n.content}</p>
            </>
          ) : (
            <>
              <p className="note">알리는 값과 적용일은 고칠 수 없어요. 값을 바꾸려면 정책 관리에서 변경해 주세요.</p>
              <NoticeFields name={n.title} value={draft} onChange={(patch) => setDraft((d) => ({ ...d, ...patch }))} minHomeUntil={minHomeUntil} />
              {textChanged && <p className="note">저장하면 이용자 화면에 &quot;수정됨&quot;과 날짜가 보여요.</p>}
              {(error || apiError) && <p role="alert" className="text-[13px] font-medium text-red-600">{error || apiError}</p>}
              <button type="button" disabled={saving || !!error || (!textChanged && !homeChanged)} onClick={save} className="btn btn-primary disabled:opacity-40">저장</button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
