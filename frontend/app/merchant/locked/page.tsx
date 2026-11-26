"use client";

import Link from "next/link";
import { useEffect } from "react";
import HomeLink from "@/components/HomeLink";
import { logout } from "@/lib/auth";

// 점주가 간편비밀번호를 5회 틀려 잠긴 경우. 로그아웃하고 점주 로그인으로 안내한다. 잠긴 지 10분이 지나면 서버가 자동으로 풀어 준다
export default function MerchantLockedPage() {
  useEffect(() => logout(), []);
  return (
    <div className="flex flex-1 flex-col px-5 pt-14">
      <HomeLink />
      <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full border-2 border-red-700 text-2xl font-bold text-red-700">
        !
      </div>
      <h1 className="text-center text-xl font-medium">간편비밀번호가 잠겼습니다</h1>
      <p className="mt-1 text-center text-[13px] text-zinc-500">5회 연속 틀려서 잠겼어요</p>
      <div className="mt-6 space-y-3">
        <div className="card-gray">
          <div className="font-medium">잠긴 지 10분이 지나면 다시 시도할 수 있어요</div>
          <p className="note mt-1">로그아웃했어요. 10분 뒤에 다시 로그인해 주세요.</p>
        </div>
        <Link href="/merchant/login" className="btn btn-primary">가맹점주 로그인</Link>
      </div>
    </div>
  );
}
