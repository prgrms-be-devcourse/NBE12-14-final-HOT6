"use client";

import Link from "next/link";
import CashbackPausedNotice from "@/components/CashbackPausedNotice";
import { useCashbackPaused } from "@/lib/cashbackStatus";

// 결제 비밀번호를 입력하는 동안 적립이 중단되면 알려 준다. 중단이 아니면 아무것도 보이지 않는다. 결제는 막지 않는다
export default function PinPauseNotice() {
  const paused = useCashbackPaused();
  if (!paused) return null;
  return (
    <div className="px-5 pt-3">
      <CashbackPausedNotice>
        <Link href="/pay/scan" className="mt-1.5 block font-bold underline">결제창으로 돌아가기</Link>
      </CashbackPausedNotice>
    </div>
  );
}
