"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { clearSession } from "@/lib/auth";

// 세션을 지우고 href 로 이동한다.
export default function LogoutLink({ href, className, children }: { href: string; className?: string; children: ReactNode }) {
  return (
    <Link href={href} className={className} onClick={clearSession}>
      {children}
    </Link>
  );
}
