import Link from "next/link";
import Field from "@/components/Field";
import Header from "@/components/Header";
import { user } from "@/lib/mock";

export default function ProfilePage() {
  return (
    <>
      <Header title="내정보 수정" back="/my" />
      <form className="space-y-3 px-5">
        <Field label="이름" defaultValue={user.name} />
        <Field label="이메일 (변경 불가)" defaultValue={user.email} readOnly />
        <div className="pt-2 text-sm font-semibold">비밀번호 변경</div>
        <Field label="현재 비밀번호" type="password" />
        <Field label="새 비밀번호" type="password" />
        <Link href="/pin-setup" className="btn btn-outline">간편비밀번호 변경</Link>
        <Link href="/my" className="btn btn-primary">저장</Link>
        <p className="note">※ 마이페이지 하위 화면</p>
      </form>
    </>
  );
}
