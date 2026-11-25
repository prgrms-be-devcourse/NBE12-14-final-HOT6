"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import Amount from "@/components/Amount";
import ChargeLimitNote from "@/components/ChargeLimitNote";
import Header from "@/components/Header";
import { HelpButton, HelpTip } from "@/components/Help";
import { ICONS } from "@/components/icons";
import Progress from "@/components/Progress";
import { getCashbackSummary, getMonthlyLimit } from "@/lib/cashbackApi";
import { getMyWallet } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";

// 충전·환불을 마치고 돌아오면 ?charged=금액 / ?refunded=금액 으로 완료 안내를 띄운다
function doneBanner(params: URLSearchParams) {
  const charged = Number(params.get("charged"));
  const refunded = Number(params.get("refunded"));
  if (Number.isInteger(charged) && charged > 0) return `${won(charged)} 충전이 완료되었습니다`;
  if (Number.isInteger(refunded) && refunded > 0) return `${won(refunded)} 환불이 완료되었습니다`;
  return null;
}

// 초록 배경의 캐시백 카드 안에서 쓰는 안내창: 흰 배경에 초록 점선
const CARD_TIP = "!border-emerald-500 !bg-white";

// 잔액 옆의 아래 화살표: 그 잔액의 내역 화면으로 간다
function HistoryArrow({ href, label }: { href: string; label: string }) {
  return (
    <Link href={href} aria-label={label} className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-slate-300 text-slate-700 hover:bg-slate-50">
      {ICONS.chevronDown}
    </Link>
  );
}

function WalletBody() {
  const banner = doneBanner(useSearchParams());
  const wallet = useLoad(getMyWallet, []);
  const summary = useLoad(getCashbackSummary, []);
  const limit = useLoad(getMonthlyLimit, []);
  const [help, setHelp] = useState(false); // 적립 예정 안내
  const [limitHelp, setLimitHelp] = useState(false); // 월 적립 한도 안내
  const w = wallet.data;

  return (
    <div className="px-5 pb-4">
      {banner && <p role="status" className="mb-3 rounded-lg bg-brand-600 py-2.5 text-center text-[13px] font-bold text-white">{banner}</p>}
      {wallet.error && <p role="alert" className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{wallet.error}</p>}
      <section className="card !p-5">
        <span className="text-[13px] font-medium text-slate-500">데브머니 잔액</span>
        <div className="mt-0.5 flex items-center justify-between gap-2">
          {w ? <Amount value={w.devMoney} size="xl" /> : <span className="note">불러오는 중…</span>}
          <HistoryArrow href="/wallet/history" label="이용내역 보기" />
        </div>
        {w && <ChargeLimitNote wallet={w} className="mt-2 border-t border-slate-200 pt-2.5" />}
        <div className="mt-4 flex gap-2.5">
          <Link href="/wallet/charge" className="btn btn-primary">충전</Link>
          <Link href="/wallet/refund" className="btn btn-danger">환불</Link>
        </div>
      </section>

      <section className="card mt-3 !bg-emerald-50 !p-5">
        <span className="text-[13px] font-medium text-emerald-800">캐시백 잔액</span>
        <div className="mt-0.5 flex items-center justify-between gap-2">
          {w ? <Amount value={w.cashbackBalance} kind={w.cashbackBalance < 0 ? "out" : "cashbackDeep"} size="xl" /> : <span className="note">불러오는 중…</span>}
          <HistoryArrow href="/cashback" label="캐시백 내역 보기" />
        </div>
        {w && w.cashbackBalance < 0 && (
          <p className="note mt-1">캐시백 잔액이 음수예요. 다음 적립분으로 먼저 채워지고, 그동안은 캐시백을 쓸 수 없어요.</p>
        )}
        <div className="mt-2 flex items-center justify-between">
          <span className="flex items-center gap-1.5">
            <span className="text-[13px] leading-[18px] text-slate-500">적립 예정</span>
            <HelpButton open={help} onClick={() => setHelp((v) => !v)} controls="pending-help" label="적립 예정 안내 보기" />
          </span>
          {summary.data ? <Amount value={summary.data.pendingAmount} kind="wait" size="md" /> : <span className="note">…</span>}
        </div>
        <HelpTip id="pending-help" open={help} className={`mt-2 ${CARD_TIP}`}>적립 예정은 지급되기 전까지 쓸 수 없어요</HelpTip>
        <div className="mt-3 flex items-baseline justify-between border-t border-slate-200 pt-3">
          <span className="font-bold">캐시백 적립</span>
          <span className="text-[13px]">{limit.data ? `${limit.data.usedAmount.toLocaleString("ko-KR")} / ${won(limit.data.limit)}` : "…"}</span>
        </div>
        {limit.data && (
          <>
            <div className="mt-3"><Progress value={limit.data.usedAmount} max={limit.data.limit} /></div>
            <div className="mt-3 flex items-center justify-between rounded-lg bg-white px-3 py-2">
              <span className="flex items-center gap-1.5">
                <span className="text-[13px] font-semibold leading-[18px] text-emerald-800">월 적립 한도 잔여</span>
                <HelpButton open={limitHelp} onClick={() => setLimitHelp((v) => !v)} controls="limit-help" label="월 적립 한도 안내 보기" />
              </span>
              <span className="amt-cb text-base font-bold">{won(limit.data.remaining)}</span>
            </div>
            <HelpTip id="limit-help" open={limitHelp} className={`mt-2 ${CARD_TIP}`}>
              캐시백은 한도에 맞춰 적립
              <br />
              (한도 : 3,000원 여유, 적립 예정 캐시백 : 5,000원 → 3,000원만 적립)
            </HelpTip>
            <p className="note mt-1.5 font-semibold">월 적립 한도 {won(limit.data.limit)} 중 {won(limit.data.usedAmount)} 적립 (적립 예정 포함)</p>
          </>
        )}
        {limit.error && <p role="alert" className="mt-2 text-[13px] text-red-600">{limit.error}</p>}
      </section>
    </div>
  );
}

export default function WalletPage() {
  return (
    <>
      <Header title="내 지갑" />
      <Suspense fallback={null}>
        <WalletBody />
      </Suspense>
    </>
  );
}
