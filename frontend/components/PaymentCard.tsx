import HistoryCard from "@/components/HistoryCard";
import { won } from "@/lib/format";
import { fmt } from "@/lib/history";
import type { UserPaymentItem } from "@/lib/types";

// 결제 한 건. 누르면 영수증(ReceiptModal)을 연다
export default function PaymentCard({ payment: p, onClick }: { payment: UserPaymentItem; onClick: () => void }) {
  const done = p.status === "COMPLETED";
  return (
    <HistoryCard
      title={p.merchantName ?? "알 수 없는 가맹점"}
      amount={won(p.amount)}
      amountClass={done ? "amt-out" : "text-slate-400 line-through"}
      detail={fmt(p.completedAt)}
      badge={done ? "완료" : "취소"}
      badgeClass={done ? "badge-ok" : "badge-bad"}
      onClick={onClick}
    />
  );
}
