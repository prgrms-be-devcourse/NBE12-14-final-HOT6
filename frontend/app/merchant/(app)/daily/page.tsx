import Header from "@/components/Header";
import MerchantTabs from "@/components/MerchantTabs";
import { won } from "@/lib/format";
import { dailySettles } from "@/lib/mock";

export default function MerchantDailyPage() {
  return (
    <>
      <Header title="매출 · 정산" />
      <MerchantTabs />
      <div className="space-y-3 px-5 pb-5">
        <section className="card">
          <div className="flex items-center justify-between"><span className="font-medium">2026.11.17 일 정산</span><span className="badge badge-ok">생성됨</span></div>
          <dl className="mt-3 space-y-1.5 text-sm">
            <div className="flex justify-between"><dt>결제</dt><dd>{won(340000)}</dd></div>
            <div className="flex justify-between"><dt>− 취소</dt><dd className="amt-out">{won(20000)}</dd></div>
            <div className="flex justify-between border-t border-slate-200 pt-1.5 font-semibold"><dt>= 순매출</dt><dd className="amt-in">{won(320000)}</dd></div>
            <div className="flex justify-between text-zinc-500"><dt>이월</dt><dd>{won(0)}</dd></div>
          </dl>
        </section>
        <h2 className="px-1.5 pt-2 text-base font-medium">최근 정산</h2>
        <ul>{dailySettles.slice(1, 5).map((d) => <li key={d.date} className="flex justify-between border-t border-zinc-200 px-1.5 py-2.5"><span>{d.date}</span><span className="font-medium">{won(d.net)}</span></li>)}</ul>
        <div className="card-dash"><ul className="space-y-0.5"><li>• 같은 날짜 정산은 중복 생성 불가</li><li>• 이월 금액은 다음 정산에 합산</li></ul></div>
      </div>
    </>
  );
}
