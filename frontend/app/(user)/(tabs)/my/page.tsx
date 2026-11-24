import Link from "next/link";
import LogoutLink from "@/components/LogoutLink";
import Header from "@/components/Header";
import { won } from "@/lib/format";
import { user, wallet } from "@/lib/mock";

const MENUS = [
  { href: "/wallet", label: "지갑 거래내역" },
  { href: "/pay/history", label: "결제 내역" },
  { href: "/cashback/accruals", label: "캐시백 적립내역" },
  { href: "/pin-setup", label: "간편비밀번호 변경" },
  { href: "/cashback", label: "알림 (정책 변경 예고)" },
];

export default function MyPage() {
  return (
    <>
      <Header title="마이페이지" />
      <div className="space-y-3 px-5 pb-4">
        <section className="card flex items-center justify-between">
          <div>
            <div className="font-medium">{user.name}</div>
            <div className="note">{user.email}</div>
          </div>
          <Link href="/my/profile" className="btn btn-outline btn-sm !w-auto">내정보 수정</Link>
        </section>
        <section className="grid grid-cols-2 gap-2">
          <div className="card-gray !p-3"><div className="note">지갑 잔액</div><div className="mt-0.5 font-bold text-slate-900">{won(wallet.devMoney)}</div></div>
          <div className="card-gray !p-3"><div className="note">이번 달 캐시백</div><div className="mt-0.5 font-bold text-emerald-600">{won(wallet.monthlyCashback)}</div></div>
        </section>
        <ul className="card !p-0">
          {MENUS.map((m, i) => (
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
