import Link from "next/link";
import Logo from "@/components/Logo";

const ROLES = [
  { href: "/login", title: "이용자", badge: "모바일웹", tone: "border-t-blue-500", icon: "bg-blue-50 text-blue-600", glyph: "₩", desc: "QR로 결제하고 캐시백을 받아요. 데브머니를 충전하고 내 지갑을 관리합니다.", cta: "이용자 로그인", sub: { href: "/signup", label: "회원가입" } },
  { href: "/merchant/login", title: "가맹점주", badge: "모바일웹", tone: "border-t-emerald-500", icon: "bg-emerald-50 text-emerald-600", glyph: "▦", desc: "동적 QR을 만들어 결제를 받고, 매출과 일·월 정산을 확인합니다.", cta: "가맹점주 로그인", sub: { href: "/merchant/register", label: "가맹점 등록" } },
  { href: "/admin/login", title: "운영자", badge: "웹", tone: "border-t-slate-700", icon: "bg-slate-100 text-slate-700", glyph: "◧", desc: "캐시백 정책과 예산을 관리하고, 대시보드와 정산·원장 대사를 봅니다.", cta: "운영자 로그인" },
];

export default function HomePage() {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="mx-auto flex w-full max-w-5xl items-center justify-between px-5 py-5">
        <Logo className="text-xl" />
        <nav className="flex gap-4 text-xs text-zinc-500"><Link href="/dev/api" className="underline-offset-2 hover:underline">API 테스트</Link><Link href="/dev" className="underline-offset-2 hover:underline">화면 목록</Link></nav>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-5 pb-16 pt-10 sm:pt-16">
        <h1 className="text-3xl font-bold leading-tight tracking-tight sm:text-5xl">QR로 결제하고,<br /><span className="text-blue-600">캐시백</span>까지 한 번에.</h1>
        <p className="mt-4 max-w-xl text-zinc-600">데브머니로 결제하면 캐시백이 쌓이는 QR 결제 서비스입니다. 사용하는 역할을 선택해 로그인하세요.</p>
        <div className="mt-10 grid gap-4 md:grid-cols-3">
          {ROLES.map((r) => (
            <section key={r.title} className={`card flex flex-col border-t-4 !p-6 ${r.tone}`}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3"><span className={`flex h-10 w-10 items-center justify-center rounded-xl text-lg font-bold ${r.icon}`} aria-hidden>{r.glyph}</span><h2 className="text-xl font-semibold">{r.title}</h2></div>
                <span className="badge">{r.badge}</span>
              </div>
              <p className="mt-3 flex-1 text-sm leading-relaxed text-zinc-600">{r.desc}</p>
              <Link href={r.href} className="btn btn-primary mt-6">{r.cta}</Link>
              {r.sub && <Link href={r.sub.href} className="mt-3 text-center text-[13px] font-medium text-zinc-600 underline-offset-2 hover:underline">{r.sub.label}</Link>}
            </section>
          ))}
        </div>
      </main>
      <footer className="border-t border-zinc-200 py-5 text-center text-xs text-zinc-500">© DevPay · 캐시백 QR 결제 서비스</footer>
    </div>
  );
}
