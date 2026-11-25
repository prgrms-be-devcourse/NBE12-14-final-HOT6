import { ICONS } from "@/components/icons";
import { signed } from "@/lib/format";

export type TxKind = "charge" | "refund" | "payment" | "cashback";
export type Tx = { id: string; title: string; date: string; amount: number; kind: TxKind; isNew?: boolean };

// 입금(충전)=파랑, 출금(환불·결제)=빨강, 캐시백=초록
const STYLE: Record<TxKind, { icon: React.ReactNode; chip: string; amount: string; label: string }> = {
  charge: { icon: ICONS.arrowIn, chip: "bg-blue-50 text-blue-600", amount: "amt-in", label: "입금" },
  refund: { icon: ICONS.arrowOut, chip: "bg-red-50 text-red-600", amount: "amt-out", label: "환불" },
  payment: { icon: ICONS.arrowOut, chip: "bg-red-50 text-red-600", amount: "amt-out", label: "결제" },
  cashback: { icon: ICONS.star, chip: "bg-emerald-50 text-emerald-600", amount: "amt-cb", label: "캐시백" },
};

export default function TxRow({ tx }: { tx: Tx }) {
  const st = STYLE[tx.kind];
  return (
    <li className={`flex items-center gap-3 border-t border-slate-200 px-1.5 py-3 first:border-t-0 ${tx.isNew ? "rounded-lg bg-brand-50" : ""}`}>
      <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${st.chip}`} role="img" aria-label={st.label}>{st.icon}</span>
      <div className="min-w-0 flex-1">
        <div className="font-medium">
          {tx.title} {tx.isNew && <span className="badge badge-ok ml-1">NEW</span>}
        </div>
        <div className="note">{tx.date}</div>
      </div>
      <div className={`font-bold tabular-nums ${st.amount}`}>{signed(tx.amount)}</div>
    </li>
  );
}
