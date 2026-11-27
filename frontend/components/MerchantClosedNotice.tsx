import Link from "next/link";
import type { MerchantStatus } from "@/lib/types";

const TEXT: Record<Exclude<MerchantStatus, "ACTIVE">, { title: string; body: string }> = {
  CLOSING: {
    title: "해지 진행 중이에요",
    body: "신청 이후 정산이 모두 완료되면 해지가 완료됩니다. 그동안 QR 생성과 결제는 할 수 없고, 정산과 결제 내역은 볼 수 있어요.",
  },
  CLOSED: {
    title: "해지가 완료됐어요",
    body: "QR 생성과 결제는 할 수 없어요. 정산과 결제 내역은 계속 볼 수 있어요.",
  },
};

// 해지를 신청했거나 해지된 가맹점에 보여 주는 안내. QR 화면과 해지 화면이 함께 쓴다
export default function MerchantClosedNotice({ status, showSettlementLink = true }: { status: Exclude<MerchantStatus, "ACTIVE">; showSettlementLink?: boolean }) {
  const { title, body } = TEXT[status];
  return (
    <div className="space-y-3">
      <section className="card text-center" role="status">
        <span className={`badge ${status === "CLOSING" ? "badge-warn" : "badge-bad"}`}>{status === "CLOSING" ? "해지 진행 중" : "해지 완료"}</span>
        <div className="mt-2 text-lg font-bold">{title}</div>
        <p className="note mt-2">{body}</p>
      </section>
      {showSettlementLink && <Link href="/merchant/daily" className="btn btn-outline">정산 보러 가기</Link>}
    </div>
  );
}
