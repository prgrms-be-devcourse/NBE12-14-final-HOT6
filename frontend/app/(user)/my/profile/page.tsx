"use client";

import Link from "next/link";
import Field from "@/components/Field";
import Header from "@/components/Header";
import { useLoad } from "@/lib/useLoad";
import { getMyInfo } from "@/lib/userApi";

export default function ProfilePage() {
  const me = useLoad(getMyInfo, []);
  return (
    <>
      <Header title="내정보 수정" back="/my" />
      {/* 내 정보 조회만 있고 수정 API 는 아직 없다. 값은 서버 기준으로 채우고 저장은 막아 둔다 */}
      <form className="space-y-3 px-5" key={me.data?.email ?? "loading"} onSubmit={(e) => e.preventDefault()}>
        {me.error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{me.error}</p>}
        <Field label="이름" defaultValue={me.data?.name ?? ""} readOnly />
        <Field label="이메일 (변경 불가)" defaultValue={me.data?.email ?? ""} readOnly />
        <div className="pt-2 text-sm font-semibold">비밀번호 변경</div>
        <Field label="현재 비밀번호" type="password" />
        <Field label="새 비밀번호" type="password" />
        <Link href="/pin-setup" className="btn btn-outline">간편비밀번호 변경</Link>
        <button type="submit" disabled className="btn btn-primary disabled:opacity-40">저장</button>
        <p className="note">이름·비밀번호 변경은 아직 지원하지 않아요.</p>
      </form>
    </>
  );
}
