import Link from "next/link";
import Logo from "@/components/Logo";
import AdminNav from "@/components/AdminNav";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen md:flex">
      <aside className="border-b border-zinc-300 bg-white p-4 md:w-56 md:border-b-0 md:border-r">
        <Logo className="mb-1 text-lg" />
        <p className="mb-4 text-xs text-zinc-500">운영자 콘솔</p>
        <AdminNav />
        <Link href="/admin/login" className="mt-4 block px-3 py-2 text-sm text-zinc-500 hover:text-zinc-900">로그아웃</Link>
      </aside>
      <main className="flex-1 p-5 md:p-8">{children}</main>
    </div>
  );
}
