"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ICONS } from "@/components/icons";

const TABS = [
  { href: "/wallet", label: "홈", icon: ICONS.home, match: ["/wallet"] },
  { href: "/pay/scan", label: "QR 결제", icon: ICONS.qr, match: ["/pay"] },
  { href: "/stores", label: "가맹점", icon: ICONS.store, match: ["/stores"] },
  { href: "/cashback", label: "캐시백", icon: ICONS.gift, match: ["/cashback"] },
  { href: "/my", label: "마이페이지", icon: ICONS.user, match: ["/my"] },
];

export default function TabBar() {
  const path = usePathname();
  return (
    <nav className="sticky bottom-0 mt-auto grid grid-cols-5 border-t border-slate-200 bg-white" aria-label="하단 메뉴">
      {TABS.map((t) => {
        const on = t.match.some((m) => path.startsWith(m));
        return (
          <Link
            key={t.href}
            href={t.href}
            aria-current={on ? "page" : undefined}
            className={`flex flex-col items-center gap-1 pb-2.5 pt-2.5 text-[12px] tracking-tight ${on ? "font-bold text-blue-600" : "text-slate-500"}`}
          >
            {t.icon}
            {t.label}
          </Link>
        );
      })}
    </nav>
  );
}
