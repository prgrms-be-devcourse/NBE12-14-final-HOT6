import Header from "@/components/Header";
import MerchantTabs from "@/components/MerchantTabs";
import { won } from "@/lib/format";
import { dailySettles } from "@/lib/mock";

export default function MerchantMonthlyPage() {
  const max = Math.max(...dailySettles.map((d) => d.net));
  return (
    <>
      <Header title="매출 · 정산" />
      <MerchantTabs />
      <div className="space-y-3 px-5 pb-5">
        <section className="card">
          <div className="flex items-center justify-between"><span className="font-medium">2026년 11월 월 정산</span><span className="badge badge-warn">진행중</span></div>
          <div className="note mt-3">월 순매출</div>
          <div className="amt-in text-2xl font-bold">{won(5640000)}</div>
        </section>
        <section className="card">
          <div className="mb-2 flex items-baseline justify-between"><span className="text-sm font-semibold">일별 순매출</span><span className="note">11.01 ~ 11.12</span></div>
          <div className="flex h-28 items-end gap-1.5" role="img" aria-label="일별 순매출 막대 그래프">
            {[...dailySettles].reverse().map((d) => <div key={d.date} className="flex flex-1 flex-col items-center gap-1"><div className="w-full rounded-t bg-blue-500" style={{ height: `${(d.net / max) * 100}%` }} /><span className="text-[10px] text-zinc-500">{d.date}</span></div>)}
          </div>
          <p className="note mt-2">이월 {won(0)}</p>
        </section>
        <div className="card-dash"><ul className="space-y-0.5"><li>• 월 정산은 일 정산 합계 기준</li><li>• 월 마감 후 확정</li></ul></div>
      </div>
    </>
  );
}
