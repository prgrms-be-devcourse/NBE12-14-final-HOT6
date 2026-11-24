import { Suspense } from "react";
import Link from "next/link";
import LoginForm from "@/components/LoginForm";
import HomeLink from "@/components/HomeLink";
import Logo from "@/components/Logo";
import { safeNext } from "@/lib/format";

// next: 로그인(과 간편비밀번호 확인)을 마친 뒤 돌아갈 화면. 예: 가맹점 등록
async function LoginBody({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  const to = safeNext(next, "");
  return <LoginForm audience="user" next={to || undefined} />;
}

export default function LoginPage({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  return (
    <div className="flex flex-1 flex-col px-5 pt-14">
      <HomeLink />
      <div className="mx-auto mb-2"><Logo className="text-2xl" /></div>
      <p className="mb-8 text-center text-sm text-zinc-500">회원</p>
      <h1 className="mb-4 text-xl font-medium">로그인</h1>
      <Suspense fallback={null}>
        <LoginBody searchParams={searchParams} />
      </Suspense>
      <p className="mt-auto py-6 text-center text-[13px] text-zinc-500">
        계정이 없으신가요? <Link href="/signup" className="font-semibold text-zinc-900">회원가입 →</Link>
      </p>
    </div>
  );
}
