"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import Header from "@/components/Header";
import Field from "@/components/Field";
import { cleanName, loadSignupDraft, NAME_RULE, PASSWORD_RULE, PHONE_RULE, saveSignupDraft } from "@/lib/signupDraft";

export default function SignupPage() {
  const router = useRouter();
  // 2단계에서 돌아오면 입력값을 그대로 채워 둔다
  const [init] = useState(() => loadSignupDraft());
  const [name, setName] = useState(init?.name ?? "");
  const [email, setEmail] = useState(init?.email ?? "");
  const [phone, setPhone] = useState(init?.phone ?? "");
  const [password, setPassword] = useState(init?.password ?? "");
  const [confirm, setConfirm] = useState(init?.password ?? "");
  const [agree, setAgree] = useState(!!init);
  const [tried, setTried] = useState(false);

  const errors = {
    name: !name.trim() ? "이름을 입력해 주세요" : NAME_RULE.test(name.trim()) ? "" : "이름은 한글 또는 영문 중 하나로만 입력해 주세요",
    email: /^\S+@\S+\.\S+$/.test(email.trim()) ? "" : "이메일 형식을 확인해 주세요",
    phone: PHONE_RULE.test(phone) ? "" : "하이픈 없이 숫자만 입력해 주세요 (예: 01012345678)",
    password: PASSWORD_RULE.test(password) ? "" : "8~15자, 영문·숫자·특수문자(!@#%^&*)를 모두 포함해 주세요",
    confirm: confirm === password ? "" : "비밀번호가 일치하지 않아요",
    agree: agree ? "" : "약관에 동의해 주세요",
  };
  const show = (k: keyof typeof errors) => (tried ? errors[k] : "");

  const next = (e: FormEvent) => {
    e.preventDefault();
    setTried(true);
    if (Object.values(errors).some(Boolean)) return;
    saveSignupDraft({ name: name.trim(), email: email.trim(), phone, password });
    router.push("/pin-setup");
  };

  return (
    <>
      <Header title="회원가입" right="1 / 2" />
      <form className="space-y-3 px-5" onSubmit={next} noValidate>
        <Field label="이름" value={name} onChange={(v) => setName(cleanName(v))} autoComplete="name" maxLength={100} hint="한글 또는 영문 (띄어쓰기는 영문 이름만)" error={show("name")} />
        <Field label="이메일" type="email" value={email} onChange={setEmail} autoComplete="email" inputMode="email" error={show("email")} />
        <Field label="전화번호" value={phone} onChange={(v) => setPhone(v.replace(/\D/g, "").slice(0, 11))} inputMode="numeric" autoComplete="tel" placeholder="01012345678" hint="하이픈 없이 숫자만 입력" error={show("phone")} />
        <Field label="비밀번호" type="password" value={password} onChange={setPassword} autoComplete="new-password" maxLength={15} hint="8~15자 · 영문, 숫자, 특수문자(!@#%^&*) 포함" error={show("password")} />
        <Field label="비밀번호 확인" type="password" value={confirm} onChange={setConfirm} autoComplete="new-password" maxLength={15} error={show("confirm")} />
        <label className="flex items-center gap-2 py-1 text-[13px]">
          <input type="checkbox" className="h-4 w-4" checked={agree} onChange={(e) => setAgree(e.target.checked)} /> 이용약관 · 개인정보 수집 동의
        </label>
        {show("agree") && <p role="alert" className="text-[12px] font-medium text-red-600">{show("agree")}</p>}
        <button type="submit" className="btn btn-primary">다음 · 간편비밀번호 설정</button>
        <p className="note text-center">가입하면 모의계좌(초기 잔액 200만원)와 지갑이 만들어져요</p>
      </form>
      <p className="mt-auto py-6 text-center text-[13px] text-zinc-500">
        이미 계정이 있으신가요? <Link href="/login" className="font-semibold text-zinc-900">로그인 →</Link>
      </p>
    </>
  );
}
