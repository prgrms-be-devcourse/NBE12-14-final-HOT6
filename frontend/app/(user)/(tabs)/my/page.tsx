"use client";

import Link from "next/link";
import LogoutLink from "@/components/LogoutLink";
import Header from "@/components/Header";
import { getCashbackSummary } from "@/lib/cashbackApi";
import { getMyWallet } from "@/lib/endpoints";
import { won } from "@/lib/format";
import { useLoad } from "@/lib/useLoad";
import { getMyInfo } from "@/lib/userApi";

const MENUS = [
  { href: "/wallet/history", label: "이용내역" },
  { href: "/pay/history", label: "결제 내역" },
  { href: "/cashback/accruals", label: "캐시백 내역" },
  { href: "/cashback/notices", label: "알림 (정책 변경 예고)" },
];

export default function MyPage() {
  const me = useLoad(getMyInfo, []);
  const wallet = useLoad(getMyWallet, []);
  const summary = useLoad(getCashbackSummary, []);
  return (
    <>
      <Header title="마이페이지" />
      <div className="space-y-3 px-5 pb-4">
        {me.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{me.error}</p>}
        <section className="card flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2 font-medium">{me.data?.name ?? "…"}{me.data?.pinLocked && <span className="badge badge-bad">간편비밀번호 잠김</span>}</div>
            <div className="note">{me.data?.email ?? ""}</div>
          </div>
          <Link href="/my/profile" className="btn btn-outline btn-sm !w-auto">내정보 수정</Link>
        </section>
        {me.data?.needsRelogin && (
          <p role="status" className="rounded-lg bg-amber-50 px-3 py-2 text-[13px] text-amber-900">회원 정보가 바뀌었어요. 로그아웃 후 다시 로그인하면 적용돼요.</p>
        )}
        <section className="grid grid-cols-2 gap-2">
          <div className="card-gray !p-3"><div className="note">지갑 잔액</div><div className="mt-0.5 font-bold text-slate-900">{wallet.data ? won(wallet.data.devMoney) : "…"}</div></div>
          <div className="card-gray !p-3"><div className="note">이번 달 캐시백</div><div className="mt-0.5 font-bold text-emerald-600">{summary.data ? won(summary.data.earnedAmount) : "…"}</div></div>
        </section>
        <ul className="card !p-0">
          {/* 점주가 되기 전(USER)인 회원에게만 가맹점 등록 입구를 보여 준다 */}
          {(me.data?.role === "USER" ? [...MENUS, { href: "/merchant/register", label: "가맹점 등록하기" }] : MENUS).map((m, i) => (
            <li key={m.label} className={i ? "border-t border-zinc-200" : ""}>
              <Link href={m.href} className="flex items-center justify-between px-4 py-3.5 text-[15px]">
                {m.label}<span aria-hidden className="text-zinc-400">›</span>
              </Link>
            </li>
          ))}
        </ul>
        <div className="flex gap-2.5 pt-1">
          <LogoutLink href="/" className="btn btn-outline">로그아웃</LogoutLink>
          <Link href="/my/withdraw" className="btn btn-danger">회원 탈퇴</Link>
        </div>
      </div>
    </>
  );
}
