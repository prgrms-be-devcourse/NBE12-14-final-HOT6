"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const TABS = [
  { href: "/merchant/daily", label: "일 정산" },
  { href: "/merchant/monthly", label: "월 정산" },
];

export default function MerchantTabs() {
  const path = usePathname();
  return (
    <div className="mx-5 mb-4 flex gap-2" role="tablist">
      {TABS.map((t) => (
        <Link key={t.href} href={t.href} role="tab" aria-selected={path === t.href} className={`chip flex-1 text-center ${path === t.href ? "chip-on" : ""}`}>
          {t.label}
        </Link>
      ))}
    </div>
  );
}
