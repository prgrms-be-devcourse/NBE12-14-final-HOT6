// 적립이 중단된 상태에서 결제 전에 보여 주는 안내 (결제창·PIN 화면 공통)
export default function CashbackPausedNotice({ children }: { children?: React.ReactNode }) {
  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-3.5 py-2.5 text-[13px] text-red-700">
      <b className="block">캐시백 적립이 중단됐어요</b>
      <span className="font-medium">이번 결제는 캐시백이 적립되지 않아요. 결제는 그대로 진행돼요.</span>
      {children}
    </div>
  );
}
