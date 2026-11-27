"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ICONS } from "@/components/icons";

const ITEMS = [
  { href: "/admin", label: "대시보드", icon: ICONS.chart },
  { href: "/admin/budget", label: "예산 관리", icon: ICONS.gauge },
  { href: "/admin/policy", label: "정책 관리", icon: ICONS.sliders },
  { href: "/admin/notices", label: "공지 관리", icon: ICONS.bell },
  { href: "/admin/earnings", label: "적립 관리", icon: ICONS.payout },
  { href: "/admin/settle", label: "정산 · 원장 대사", icon: ICONS.scale },
];

export default function AdminNav() {
  const path = usePathname();
  return (
    <nav className="flex gap-1 md:flex-col" aria-label="운영팀 메뉴">
      {ITEMS.map((i) => (
        <Link key={i.href} href={i.href} aria-current={path === i.href ? "page" : undefined} className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${path === i.href ? "bg-white font-bold text-brand-600" : "text-white/90 hover:bg-white/10"}`}>{i.icon}{i.label}</Link>
      ))}
    </nav>
  );
}
