import Image from "next/image";
import Link from "next/link";
import HowItWorks from "@/components/HowItWorks";
import Logo from "@/components/Logo";

import { ICONS } from "@/components/icons";

const ROLES = [
  { href: "/login", title: "회원", badge: "모바일웹", icon: ICONS.user, desc: "QR로 결제하고 캐시백을 받아요. 지갑과 결제 내역을 한곳에서 관리합니다.", cta: "회원 로그인", sub: { href: "/signup", label: "회원가입" } },
  { href: "/merchant/login", title: "가맹점주", badge: "모바일웹", icon: ICONS.store, desc: "QR을 만들어 결제를 받고, 매출과 일·월 정산을 확인합니다.", cta: "가맹점주 로그인", sub: { href: "/merchant/register", label: "가맹점 등록" } },
  { href: "/admin/login", title: "운영팀", badge: "웹", icon: ICONS.chart, desc: "캐시백 정책과 예산을 관리하고, 대시보드와 정산·원장 대사를 봅니다.", cta: "운영팀 로그인", sub: undefined },
];

export default function HomePage() {
  return (
    <div className="web-zoom web-screen flex flex-col">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between px-6 py-3.5">
          <Logo className="text-xl" />
          <nav className="flex items-center gap-1 text-sm font-medium text-slate-600" aria-label="주요 메뉴">
            <a href="#how" className="hidden rounded-lg px-3 py-2 hover:bg-slate-100 sm:block">이용 방법</a>
            <Link href="/merchant/login" className="hidden rounded-lg px-3 py-2 hover:bg-slate-100 md:block">가맹점주</Link>
            <Link href="/admin/login" className="hidden rounded-lg px-3 py-2 hover:bg-slate-100 md:block">운영팀</Link>
            <Link href="/login" className="ml-2 rounded-lg border border-slate-300 px-4 py-2 text-slate-900 hover:bg-slate-50">로그인</Link>
            <Link href="/signup" className="rounded-lg bg-brand-600 px-4 py-2 font-bold text-white hover:bg-brand-700">회원가입</Link>
          </nav>
        </div>
      </header>

      <main className="flex-1">
        <section className="overflow-hidden bg-brand-50/70">
          <div className="mx-auto grid w-full max-w-6xl items-center gap-10 px-6 pt-16 md:grid-cols-[1.15fr_1fr] md:pt-20">
            <div className="md:pb-20">
              <h1 className="text-[40px] font-extrabold leading-[1.2] tracking-tight sm:text-[56px]">
                QR로 결제하고{" "}
                <br />
                <span className="text-brand-600">캐시백</span>까지 한 번에
              </h1>
              <p className="mt-5 max-w-lg text-lg leading-relaxed text-slate-600">
                데브머니로 결제하면 캐시백이 쌓이는 QR 결제 서비스입니다. 충전부터 결제, 적립까지 한 앱에서 끝내세요.
              </p>
              <div className="mt-8 flex flex-wrap gap-3">
                <Link href="/signup" className="btn btn-primary !w-auto px-8 !py-3.5 text-base">지금 시작하기</Link>
                <Link href="/login" className="btn btn-outline !w-auto px-8 !py-3.5 text-base">로그인</Link>
              </div>
            </div>
            <div className="relative mx-auto w-full max-w-[340px] self-end">
              <Image
                src="/mascot.webp"
                alt="DevPay 모자를 쓰고 선물 상자를 안고 있는 다람쥐 마스코트"
                width={900}
                height={1160}
                priority
                className="h-auto w-full"
              />
              <p className="absolute right-[82%] top-[6%] hidden whitespace-nowrap rounded-2xl rounded-br-sm lg:block bg-white px-4 py-2.5 text-[15px] font-bold leading-snug shadow-[0_4px_14px_rgba(22,35,29,0.14)]">
                결제할 때마다
                <br />
                <span className="text-brand-600">캐시백</span>을 선물해 드려요
              </p>
            </div>
          </div>
        </section>

        <section id="how" className="mx-auto w-full max-w-6xl scroll-mt-20 px-6 py-16 md:py-20">
          <h2 className="text-3xl font-extrabold tracking-tight">이렇게 사용해요</h2>
          <p className="mt-2 text-slate-600">역할을 고르면 실제 화면이 움직이는 모습으로 보여 드려요.</p>
          <HowItWorks />
        </section>

        <section className="mx-auto w-full max-w-6xl px-6 py-16 md:py-20">
          <h2 className="text-3xl font-extrabold tracking-tight">역할에 맞게 시작하세요</h2>
          <ul className="mt-8 divide-y divide-slate-200 rounded-xl border border-slate-200 bg-white">
            {ROLES.map((r) => (
              <li key={r.title} className="flex flex-col gap-4 p-6 sm:flex-row sm:items-center">
                <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">{r.icon}</span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <h3 className="text-xl font-bold">{r.title}</h3>
                    <span className="badge">{r.badge}</span>
                  </div>
                  <p className="mt-1 leading-relaxed text-slate-600">{r.desc}</p>
                </div>
                <div className="flex items-center gap-4 sm:w-64 sm:flex-col sm:items-stretch sm:gap-2">
                  <Link href={r.href} className="btn btn-primary">{r.cta}</Link>
                  {r.sub && <Link href={r.sub.href} className="text-center text-sm font-medium text-slate-600 underline-offset-2 hover:underline">{r.sub.label}</Link>}
                </div>
              </li>
            ))}
          </ul>
        </section>
      </main>

      <footer className="bg-slate-900 text-slate-300">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-8 text-sm">
          <Logo inverse className="text-lg text-white" />
          <nav className="flex gap-5" aria-label="바로가기">
            <Link href="/login" className="hover:text-white">회원</Link>
            <Link href="/merchant/login" className="hover:text-white">가맹점주</Link>
            <Link href="/admin/login" className="hover:text-white">운영팀</Link>
          </nav>
          <p className="w-full text-xs text-slate-400">© DevPay · 캐시백 QR 결제 서비스</p>
        </div>
      </footer>
    </div>
  );
}
