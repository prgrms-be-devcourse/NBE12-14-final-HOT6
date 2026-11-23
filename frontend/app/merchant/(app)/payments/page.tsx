import Header from "@/components/Header";
import CancelPaymentForm from "@/components/CancelPaymentForm";
import MerchantTabs from "@/components/MerchantTabs";
import { won } from "@/lib/format";
import { merchantPayments } from "@/lib/mock";

export default function MerchantPaymentsPage() {
  return (
    <>
      <Header title="매출 · 정산" />
      <MerchantTabs />
      <div className="space-y-3 px-5 pb-5">
        <CancelPaymentForm />
        <section className="card-gray flex items-baseline justify-between"><span className="text-[13px] text-zinc-500">오늘 결제</span><span className="amt-in font-bold">5건 · {won(61500)}</span></section>
        <ul>
          {merchantPayments.map((p) => (
            <li key={p.id} className="flex items-center justify-between border-t border-zinc-200 px-1.5 py-2.5">
              <div><div className="font-medium">{p.user}</div><div className="note">{p.time}</div></div>
              <div className="text-right"><div className={`font-semibold ${p.status === "취소" ? "text-slate-400 line-through" : "amt-in"}`}>{won(p.amount)}</div><span className={`badge ${p.status === "완료" ? "badge-ok" : "badge-bad"}`}>{p.status}</span></div>
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}
