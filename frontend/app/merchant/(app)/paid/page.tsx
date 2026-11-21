import Link from "next/link";
import Header from "@/components/Header";
import { won } from "@/lib/format";

export default function MerchantPaidPage() {
  const rows = [["결제번호", "PAY-0001"], ["결제 시각", "11.17 12:31"], ["회원", "○○○님"]];
  return (
    <>
      <Header title="QR 결제 완료 확인" />
      <div className="space-y-4 px-5 pb-5">
        <div>
          <span className="label">QR</span>
          <div className="flex gap-2">{["대기중", "결제완료", "만료"].map((s) => <span key={s} className={`chip flex-1 text-center ${s === "결제완료" ? "chip-on" : ""}`}>{s}</span>)}</div>
        </div>
        <section className="card text-center">
          <div className="text-sm text-zinc-500">결제 완료</div>
          <div className="amt-in mt-1 text-3xl font-bold">{won(12000)}</div>
          <dl className="mt-4 text-left">{rows.map(([k, v]) => <div key={k} className="flex justify-between border-t border-zinc-100 py-2 text-sm"><dt className="text-zinc-500">{k}</dt><dd>{v}</dd></div>)}</dl>
        </section>
        <div className="flex gap-2.5"><Link href="/merchant/qr" className="btn btn-primary">새 QR 생성</Link><Link href="/merchant/payments" className="btn btn-outline">결제 내역 보기</Link></div>
        <p className="note text-center">QR 사용처리 완료 · 재사용 불가</p>
      </div>
    </>
  );
}
