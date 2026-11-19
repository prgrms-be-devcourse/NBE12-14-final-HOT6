import Link from "next/link";
import Field from "@/components/Field";
import Logo from "@/components/Logo";

export default function AdminLoginPage() {
  return (
    <div className="flex min-h-screen items-center justify-center px-5">
      <div className="card w-full max-w-sm !p-8">
        <Logo className="text-2xl" />
        <p className="mt-1 text-sm text-zinc-500">운영자 콘솔</p>
        <form className="mt-6 space-y-3">
          <Field label="이메일" type="email" />
          <Field label="비밀번호" type="password" />
          <Link href="/admin" className="btn btn-primary">로그인</Link>
        </form>
        <p className="note mt-4">인증 API 연동 전이라 로그인 버튼은 화면만 이동합니다.</p>
        <Link href="/" className="mt-4 block text-center text-[13px] text-zinc-500 underline-offset-2 hover:underline">← DevPay 홈</Link>
      </div>
    </div>
  );
}
