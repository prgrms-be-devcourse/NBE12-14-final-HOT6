import Link from "next/link";
import AuthGuard from "@/components/AuthGuard";
import LogoutLink from "@/components/LogoutLink";
import Logo from "@/components/Logo";
import AdminNav from "@/components/AdminNav";
import { ICONS } from "@/components/icons";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="web-zoom web-screen md:flex">
      <aside className="bg-brand-600 p-4 text-white md:w-60 md:shrink-0">
        <Logo inverse className="mb-1 text-lg text-white" />
        <p className="mb-5 text-xs text-white/75">운영팀 콘솔</p>
        <AdminNav />
        <LogoutLink href="/admin/login" className="mt-4 block rounded-lg px-3 py-2 text-sm text-white/80 hover:bg-white/10">로그아웃</LogoutLink>
      </aside>
      <main className="flex-1 p-5 md:p-8">
        <div className="mx-auto mb-3 flex max-w-5xl justify-end">
          <Link href="/" className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-200">
            {ICONS.home}
            홈으로
          </Link>
        </div>
        <AuthGuard audience="admin">{children}</AuthGuard>
      </main>
    </div>
  );
}
