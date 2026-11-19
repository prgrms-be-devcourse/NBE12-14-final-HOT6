import Link from "next/link";
import Header from "@/components/Header";
import Progress from "@/components/Progress";
import { won } from "@/lib/format";
import { accruals, wallet } from "@/lib/mock";

const BADGE = { 예정: "badge-warn", 지급완료: "badge-ok", 제외: "badge-bad" } as const;

export default function CashbackPage() {
  const recent = [accruals[0], { ...accruals[1], date: "11.05", status: "지급완료" as const, desc: "" }, accruals[2]];
  return (
    <>
      <Header title="내 캐시백" />
      <div className="space-y-3 px-5 pb-4">
        <div className="rounded-xl border border-amber-500 bg-amber-50 px-4 py-3 text-[13px] text-amber-900">
          <span className="font-semibold">정책 변경 예고</span> · 다음 달부터 요율 2.0% → 1.5%
        </div>
        <section className="card">
          <div className="note">이번 달 캐시백</div>
          <div className="mt-1 text-[28px] font-bold text-emerald-600">{won(wallet.monthlyCashback)}</div>
          <div className="mt-3"><Progress value={wallet.monthlyCashback} max={wallet.monthlyLimit} /></div>
          <p className="note mt-2">월 적립 한도 {won(wallet.monthlyLimit)} · 월 적립 한도 잔여 {won(wallet.monthlyLimit - wallet.monthlyCashback)}</p>
          <p className="mt-1 text-[13px]">적립 예정 {won(wallet.pendingCashback)} · 지급 완료 {won(wallet.paidCashback)}</p>
          <Link href="/cashback/accruals" className="btn btn-outline btn-sm mt-3">적립 내역 보기 →</Link>
        </section>
        <h2 className="px-1.5 pt-2 text-base font-medium">최근 적립</h2>
        <ul>
          {recent.map((a) => (
            <li key={a.id} className="flex items-center justify-between border-t border-zinc-200 px-1.5 py-2.5">
              <div>
                <div className="font-medium">{a.merchant} <span className={a.status === "제외" ? "text-slate-400" : "amt-cb"}>+{a.amount}원</span></div>
                <div className="note">{a.status === "제외" ? a.desc.replace("제외:", "제외 사유:") : a.status === "예정" ? "지급 예정 12.05" : "지급 완료 11.05"}</div>
              </div>
              <span className={`badge ${BADGE[a.status]}`}>{a.status}</span>
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
