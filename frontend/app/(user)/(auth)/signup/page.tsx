import Link from "next/link";
import Header from "@/components/Header";
import Field from "@/components/Field";

export default function SignupPage() {
  return (
    <>
      <Header title="회원가입" right="1 / 2" />
      <form className="space-y-3 px-5">
        <Field label="이름" />
        <Field label="이메일" type="email" />
        <Field label="비밀번호" type="password" />
        <Field label="비밀번호 확인" type="password" />
        <label className="flex items-center gap-2 py-1 text-[13px]">
          <input type="checkbox" className="h-4 w-4" /> 이용약관 · 개인정보 수집 동의
        </label>
        <Link href="/pin-setup" className="btn btn-primary">가입하기</Link>
      </form>
      <p className="mt-auto py-6 text-center text-[13px] text-zinc-500">
        이미 계정이 있으신가요? <Link href="/login" className="font-semibold text-zinc-900">로그인 →</Link>
      </p>
    </>
  );
}
