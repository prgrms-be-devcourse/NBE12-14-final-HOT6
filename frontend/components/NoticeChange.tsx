import { mmdd, noticeChange, type Notice } from "@/lib/cashbackApi";

// 요율·한도 변경 공지의 "이전 값 → 새 값" 강조 상자. 값의 변화가 없는 공지는 아무것도 그리지 않는다
export default function NoticeChange({ notice }: { notice: Notice }) {
  const change = noticeChange(notice);
  if (!change) return null;
  return (
    <div className="mt-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5">
      <div className="flex items-center justify-between gap-2 text-[12px] font-bold text-amber-800">
        <span>{change.name}</span>
        <span>{mmdd(notice.effectiveFrom)}부터</span>
      </div>
      <div className="mt-1 flex flex-wrap items-baseline justify-center gap-x-2.5 tabular-nums">
        <span className="text-base font-medium text-slate-500">{change.from}</span>
        <span aria-hidden className="text-slate-400">→</span>
        <span className="sr-only">에서</span>
        <span className="text-[22px] font-extrabold text-slate-900">{change.to}</span>
      </div>
    </div>
  );
}
