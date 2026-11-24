import Header from "@/components/Header";
import CancelPaymentForm from "@/components/CancelPaymentForm";
import MerchantTabs from "@/components/MerchantTabs";

export default function MerchantPaymentsPage() {
  return (
    <>
      <Header title="매출 · 정산" />
      <MerchantTabs />
      <div className="space-y-3 px-5 pb-5">
        <CancelPaymentForm />
        <section className="card-gray">
          <p className="text-[13px] text-zinc-600">결제 목록 조회는 아직 지원하지 않아요. 손님 영수증의 결제번호로 취소할 수 있고, 일별 매출은 일 정산에서 확인해요.</p>
        </section>
      </div>
    </>
  );
}
