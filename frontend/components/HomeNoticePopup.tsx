"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import NoticeChange from "@/components/NoticeChange";
import { getSession } from "@/lib/auth";
import { getHomeNotices, mmdd, NOTICE_BADGE, NOTICE_LABEL, type Notice } from "@/lib/cashbackApi";
import { useLoad } from "@/lib/useLoad";

const HIDE_DAYS = 7;
const SWIPE_PX = 40;
// 확인·닫기로 닫은 공지. 새로 고치기 전까지만 기억한다 (홈을 오갈 때마다 다시 뜨지 않게)
const closed = new Set<string>();

// 번호만 쓰면 DB 를 새로 만든 환경에서 번호가 다시 쓰일 때 새 공지가 숨겨진다. 등록 시각까지 함께 본다
const noticeKey = (n: Notice) => `${n.noticeId}@${n.createdAt}`;
const closedKey = (n: Notice) => `${getSession()?.userId ?? 0}:${noticeKey(n)}`;
const storageKey = () => `devpay.noticeHide.${getSession()?.userId ?? 0}`;

// 7일간 보지 않기: 공지 → 다시 보여 줄 시각. 저장소를 못 쓰면 숨기지 않는다
function readHidden(): Record<string, number> {
  try {
    const all = JSON.parse(localStorage.getItem(storageKey()) ?? "{}") as Record<string, number>;
    return Object.fromEntries(Object.entries(all).filter(([, until]) => until > Date.now()));
  } catch {
    return {};
  }
}

function hideForDays(notices: Notice[]) {
  const until = Date.now() + HIDE_DAYS * 86_400_000;
  try {
    localStorage.setItem(storageKey(), JSON.stringify({ ...readHidden(), ...Object.fromEntries(notices.map((n) => [noticeKey(n), until])) }));
  } catch {
    /* 저장하지 못하면 이번에만 닫는다 */
  }
}

// 홈에 들어오면 띄우는 공지 창. 글만 보여 주고 자동으로 넘기지 않는다
export default function HomeNoticePopup() {
  const load = useLoad(async () => {
    const list = await getHomeNotices();
    const hidden = readHidden();
    return list.filter((n) => !hidden[noticeKey(n)] && !closed.has(closedKey(n)));
  }, []);
  const [dismissed, setDismissed] = useState(false);
  // 조회에 실패하면 창을 띄우지 않는다
  const notices = load.data?.filter((n) => !closed.has(closedKey(n))) ?? [];
  if (dismissed || notices.length === 0) return null;

  const close = (hide: boolean) => {
    if (hide) hideForDays(notices);
    notices.forEach((n) => closed.add(closedKey(n)));
    setDismissed(true);
  };
  // 새 공지가 생기면 첫 장부터 다시 보여 준다
  return <Dialog key={notices.map((n) => n.noticeId).join(",")} notices={notices} onClose={close} />;
}

function Dialog({ notices, onClose }: { notices: Notice[]; onClose: (hide: boolean) => void }) {
  const [index, setIndex] = useState(0);
  const startX = useRef<number | null>(null);
  const n = notices[index];
  const move = (step: number) => setIndex((i) => Math.min(notices.length - 1, Math.max(0, i + step)));

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose(false);
      if (e.key === "ArrowLeft") setIndex((i) => Math.max(0, i - 1));
      if (e.key === "ArrowRight") setIndex((i) => Math.min(notices.length - 1, i + 1));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [notices.length, onClose]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-5" onClick={() => onClose(false)}>
      <div role="dialog" aria-modal="true" aria-label="알려드립니다" onClick={(e) => e.stopPropagation()} className="w-full max-w-[360px] overflow-hidden rounded-2xl bg-white">
        <div className="flex items-center justify-between bg-brand-600 py-2 pl-5 pr-2 text-white">
          <h2 className="text-base font-bold">알려드립니다</h2>
          <button type="button" onClick={() => onClose(false)} aria-label="닫기" className="flex h-9 w-9 items-center justify-center rounded-full text-xl leading-none hover:bg-white/10">×</button>
        </div>

        <div
          className="h-[320px] touch-pan-y overflow-y-auto px-5 py-4"
          aria-live="polite"
          onPointerDown={(e) => { startX.current = e.clientX; }}
          onPointerCancel={() => { startX.current = null; }}
          onPointerUp={(e) => {
            const dx = startX.current === null ? 0 : e.clientX - startX.current;
            startX.current = null;
            if (Math.abs(dx) >= SWIPE_PX) move(dx < 0 ? 1 : -1);
          }}
        >
          <div className="flex items-center justify-between">
            <span className={`badge ${NOTICE_BADGE[n.type]}`}>{NOTICE_LABEL[n.type]}</span>
            <span className="note">{n.createdAt.slice(0, 10)}{n.updatedAt && ` · 수정됨 ${mmdd(n.updatedAt)}`}</span>
          </div>
          <h3 className="mt-2.5 break-words text-lg font-bold">{n.title}</h3>
          <NoticeChange notice={n} />
          <p className="mt-2.5 whitespace-pre-line break-words text-[14px] leading-relaxed text-slate-700">{n.content}</p>
          <Link href={`/notices?id=${n.noticeId}`} onClick={() => onClose(false)} className="mt-3 inline-block text-[13px] font-bold text-brand-600 underline">바로가기</Link>
        </div>

        {notices.length > 1 && (
          <div className="flex items-center justify-center gap-4 border-t border-slate-100 py-1.5">
            <button type="button" onClick={() => move(-1)} disabled={index === 0} aria-label="이전 공지" className="h-8 w-8 rounded-full text-xl text-slate-700 hover:bg-slate-100 disabled:opacity-30">‹</button>
            <span className="text-[13px] font-medium tabular-nums text-slate-600">{index + 1}/{notices.length}</span>
            <button type="button" onClick={() => move(1)} disabled={index === notices.length - 1} aria-label="다음 공지" className="h-8 w-8 rounded-full text-xl text-slate-700 hover:bg-slate-100 disabled:opacity-30">›</button>
          </div>
        )}

        <div className="flex items-center justify-between gap-3 border-t border-slate-200 px-5 py-3">
          <button type="button" onClick={() => onClose(true)} className="text-[13px] font-medium text-slate-600 underline-offset-2 hover:underline">{HIDE_DAYS}일간 보지 않기</button>
          <button type="button" autoFocus onClick={() => onClose(false)} className="btn btn-primary btn-sm !w-24">확인</button>
        </div>
      </div>
    </div>
  );
}
