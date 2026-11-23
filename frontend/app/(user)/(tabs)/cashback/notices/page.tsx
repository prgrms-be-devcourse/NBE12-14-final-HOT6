"use client";

import Header from "@/components/Header";
import { getNotices, pctText, type NoticeType } from "@/lib/cashbackApi";
import { useLoad } from "@/lib/useLoad";

const TYPE_LABEL: Record<NoticeType, string> = {
  POLICY_CHANGE: "정책 변경", BUDGET_NOTICE: "소진 예고", EMERGENCY_STOP: "적립 중단", STOP_CANCELED: "중단 취소",
};
const TYPE_BADGE: Record<NoticeType, string> = {
  POLICY_CHANGE: "badge-warn", BUDGET_NOTICE: "badge-warn", EMERGENCY_STOP: "badge-bad", STOP_CANCELED: "badge-ok",
};

export default function NoticesPage() {
  const list = useLoad(() => getNotices(undefined, 0, 50), []);
  return (
    <>
      <Header title="캐시백 공지" back="/cashback" />
      <div className="px-5 pb-4">
        {list.error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-[13px] text-red-700">{list.error}</p>}
        {list.data && list.data.content.length === 0 && <p className="note">등록된 공지가 없어요.</p>}
        <ul>
          {list.data?.content.map((n) => (
            <li key={n.noticeId} className="border-t border-zinc-200 px-1.5 py-3">
              <div className="flex items-center justify-between">
                <span className={`badge ${TYPE_BADGE[n.type]}`}>{TYPE_LABEL[n.type]}</span>
                <span className="note">{n.createdAt.slice(0, 10)}</span>
              </div>
              <p className="mt-1.5 text-[14px]">{n.content}</p>
              {n.rate != null && n.previousRate != null && <p className="note mt-1">요율 {pctText(n.previousRate)} → {pctText(n.rate)}</p>}
              {n.stopAt && <p className="note mt-1">중단 예정일 {n.stopAt}</p>}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
