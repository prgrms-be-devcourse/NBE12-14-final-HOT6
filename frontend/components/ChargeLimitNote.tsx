import { won } from "@/lib/format";
import type { WalletResponse } from "@/lib/types";

// 이번 달 충전액과 월 충전 한도, 다음 줄에 남은 한도. 홈과 충전 화면에서 같이 쓴다
export default function ChargeLimitNote({ wallet: w, className = "" }: { wallet: WalletResponse; className?: string }) {
  return (
    <p className={`note ${className}`}>
      이번 달 충전 {won(w.chargedThisMonth)} / {won(w.monthlyChargeLimit)}
      <br />
      남은 한도 {won(Math.max(0, w.monthlyChargeLimit - w.chargedThisMonth))}
    </p>
  );
}
