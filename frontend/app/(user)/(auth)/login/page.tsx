import Link from "next/link";
import Field from "@/components/Field";
import Logo from "@/components/Logo";

export default function LoginPage() {
  return (
    <div className="flex flex-1 flex-col px-5 pt-14">
      <div className="mx-auto mb-2"><Logo className="text-2xl" /></div>
      <p className="mb-8 text-center text-sm text-zinc-500">이용자</p>
      <h1 className="mb-4 text-xl font-medium">로그인</h1>
      <form className="space-y-3">
        <Field label="이메일" type="email" />
        <Field label="비밀번호" type="password" />
        <Link href="/pin?next=/wallet" className="btn btn-primary">로그인</Link>
      </form>
      <div className="card-dash mt-6">
        <div className="mb-1 font-semibold">로그인 흐름</div>
        <ol className="space-y-0.5">
          <li>1) 이메일 · 비밀번호 확인</li>
          <li>2) JWT 발급</li>
          <li>3) 간편비밀번호 입력 (다음 화면)</li>
        </ol>
      </div>
      <p className="mt-auto py-6 text-center text-[13px] text-zinc-500">
        계정이 없으신가요? <Link href="/signup" className="font-semibold text-zinc-900">회원가입 →</Link>
      </p>
    </div>
  );
}
