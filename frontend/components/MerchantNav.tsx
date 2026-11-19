"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ICONS } from "@/components/icons";

const TABS = [
  { href: "/merchant/qr", label: "QR 생성", icon: ICONS.qr, match: ["/merchant/qr", "/merchant/paid"] },
  { href: "/merchant/payments", label: "매출·정산", icon: ICONS.receipt, match: ["/merchant/payments", "/merchant/daily", "/merchant/monthly"] },
  { href: "/merchant/edit", label: "내 가맹점", icon: ICONS.store, match: ["/merchant/edit"] },
  { href: "/merchant/login", label: "로그아웃", icon: ICONS.logout, match: [] as string[] },
];

export default function MerchantNav() {
  const path = usePathname();
  return (
    <nav className="sticky bottom-0 mt-auto grid grid-cols-4 border-t border-slate-200 bg-white" aria-label="하단 메뉴">
      {TABS.map((t) => {
        const on = t.match.some((m) => path.startsWith(m));
        return (
          <Link key={t.href} href={t.href} aria-current={on ? "page" : undefined} className={`flex flex-col items-center gap-1 pb-2.5 pt-2.5 text-[12px] tracking-tight ${on ? "font-bold text-emerald-600" : "text-slate-500"}`}>
            {t.icon}
            {t.label}
          </Link>
        );
      })}
    </nav>
  );
}
