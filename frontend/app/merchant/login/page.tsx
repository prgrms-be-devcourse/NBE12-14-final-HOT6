import Link from "next/link";
import Field from "@/components/Field";
import HomeLink from "@/components/HomeLink";
import Logo from "@/components/Logo";

export default function MerchantLoginPage() {
  return (
    <div className="flex flex-1 flex-col px-5 pt-14">
      <HomeLink />
      <div className="mx-auto mb-2"><Logo className="text-2xl" /></div>
      <p className="mb-8 text-center text-sm text-zinc-500">가맹점주</p>
      <h1 className="mb-4 text-xl font-medium">로그인</h1>
      <form className="space-y-3">
        <Field label="이메일" type="email" />
        <Field label="비밀번호" type="password" />
        <Link href="/merchant/qr" className="btn btn-primary">로그인</Link>
      </form>
      <p className="mt-auto py-6 text-center text-[13px] text-zinc-500">
        아직 가맹점이 없으신가요? <Link href="/merchant/register" className="font-semibold text-zinc-900">가맹점 등록 →</Link>
      </p>
    </div>
  );
}
