"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import Header from "@/components/Header";
import NoticeChange from "@/components/NoticeChange";
import { ICONS } from "@/components/icons";
import { getNotices, mmdd, NOTICE_BADGE, NOTICE_LABEL } from "@/lib/cashbackApi";
import { useLoad } from "@/lib/useLoad";

// 뒤로 가기는 마이페이지에서 오면(?from=my) 마이페이지, 그 밖에는 홈으로 간다.
// ?id=공지번호 면 그 공지를 펼친 채 내려가 테두리로 표시한다(홈 공지의 바로가기)
function NoticesBody() {
  const params = useSearchParams();
  const focusId = Number(params.get("id")) || null;
  const list = useLoad(() => getNotices(undefined, 0, 50), []);
  const loaded = !!list.data;
  // 직접 누른 공지의 펼침 여부. 누른 적이 없으면 바로가기로 온 공지만 펼친다
  const [toggled, setToggled] = useState<Record<number, boolean>>({});
  const isOpen = (id: number) => toggled[id] ?? id === focusId;

  useEffect(() => {
    if (loaded && focusId) document.getElementById(`notice-${focusId}`)?.scrollIntoView({ block: "center" });
  }, [loaded, focusId]);

  return (
    <>
      <Header title="공지사항" back={params.get("from") === "my" ? "/my" : "/wallet"} />
      <div className="px-5 pb-4">
        {list.error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-[13px] text-red-700">{list.error}</p>}
        {list.data && list.data.content.length === 0 && <p className="note">등록된 공지가 없어요.</p>}
        <ul className="space-y-2.5">
          {list.data?.content.map((n) => {
            const open = isOpen(n.noticeId);
            return (
              <li key={n.noticeId} id={`notice-${n.noticeId}`} className={`card ${n.noticeId === focusId ? "!border-brand-600 ring-2 ring-brand-100" : ""}`}>
                {/* 제목과 변경 값 어디를 눌러도 열리고 닫힌다. 키보드는 제목 버튼으로 연다 */}
                <div className="cursor-pointer" onClick={() => setToggled((t) => ({ ...t, [n.noticeId]: !open }))}>
                  <div className="flex items-center justify-between">
                    <span className={`badge ${NOTICE_BADGE[n.type]}`}>{NOTICE_LABEL[n.type]}</span>
                    <span className="note">{n.createdAt.slice(0, 10)}{n.updatedAt && ` · 수정됨 ${mmdd(n.updatedAt)}`}</span>
                  </div>
                  <h2>
                    <button type="button" aria-expanded={open} aria-controls={`notice-body-${n.noticeId}`} className="mt-2 flex w-full items-center justify-between gap-2 text-left">
                      <span className="break-words text-[15px] font-bold">{n.title}</span>
                      <span aria-hidden className={`shrink-0 text-slate-400 transition-transform ${open ? "rotate-180" : ""}`}>{ICONS.chevronDown}</span>
                    </button>
                  </h2>
                  <NoticeChange notice={n} />
                </div>
                <div id={`notice-body-${n.noticeId}`} hidden={!open} className="mt-3 border-t border-slate-100 pt-3">
                  <p className="whitespace-pre-line break-words text-[14px] leading-relaxed text-slate-700">{n.content}</p>
                  {n.stopAt && <p className="note mt-1.5">중단 예정일 {n.stopAt}</p>}
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    </>
  );
}

export default function NoticesPage() {
  return (
    <Suspense fallback={null}>
      <NoticesBody />
    </Suspense>
  );
}
