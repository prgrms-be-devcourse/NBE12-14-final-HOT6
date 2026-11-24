import Link from "next/link";
import LoginForm from "@/components/LoginForm";
import HomeLink from "@/components/HomeLink";
import Logo from "@/components/Logo";

export default function LoginPage() {
  return (
    <div className="flex flex-1 flex-col px-5 pt-14">
      <HomeLink />
      <div className="mx-auto mb-2"><Logo className="text-2xl" /></div>
      <p className="mb-8 text-center text-sm text-zinc-500">회원</p>
      <h1 className="mb-4 text-xl font-medium">로그인</h1>
      <LoginForm audience="user" />
      <p className="mt-auto py-6 text-center text-[13px] text-zinc-500">
        계정이 없으신가요? <Link href="/signup" className="font-semibold text-zinc-900">회원가입 →</Link>
      </p>
    </div>
  );
}
