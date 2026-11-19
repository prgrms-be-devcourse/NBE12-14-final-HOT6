"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ICONS } from "@/components/icons";

const ITEMS = [
  { href: "/admin", label: "대시보드", icon: ICONS.chart },
  { href: "/admin/policy", label: "정책 · 예산 관리", icon: ICONS.sliders },
  { href: "/admin/settle", label: "정산 · 원장 대사", icon: ICONS.scale },
];

export default function AdminNav() {
  const path = usePathname();
  return (
    <nav className="flex gap-1 md:flex-col" aria-label="운영자 메뉴">
      {ITEMS.map((i) => (
        <Link key={i.href} href={i.href} aria-current={path === i.href ? "page" : undefined} className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${path === i.href ? "bg-blue-600 font-semibold text-white" : "text-zinc-700 hover:bg-zinc-200"}`}>{i.icon}{i.label}</Link>
      ))}
    </nav>
  );
}
