"use client";

import Link from "next/link";
import {useRouter} from "next/navigation";
import {useState, type FormEvent} from "react";
import Header from "@/components/Header";
import Field from "@/components/Field";
import {
  cleanName,
  isMixedName,
  loadSignupDraft,
  NAME_RULE,
  PASSWORD_RULE,
  PHONE_RULE,
  saveSignupDraft
} from "@/lib/signupDraft";

// 이름 칸의 물음표를 누르면 보여 주는 입력 규칙 (서버 규칙은 SignupRequest)
const NAME_HELP = (
    <ul className="list-disc space-y-0.5 pl-4">
      <li>한글/영문으로만 입력 (Hong길동 X -&gt; 홍길동/Hong GilDong)</li>
      <li>한글 이름은 띄어쓰기 없이 입력(홍 길동 X -&gt; 홍길동)</li>
      <li>영문 이름은 단어 사이에 띄어쓰기 한 칸 가능 (Hong Gil Dong)</li>
      <li>숫자, 기호(- &apos; . 등), 다른 언어의 문자는 입력 불가</li>
      <li>외국인 등록증에 기재된 영문명만 허용 (José X -&gt; JOSE)</li>
    </ul>
);

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
  const [nameNotice, setNameNotice] = useState(""); // 이름 입력칸에서 방금 지운 문자의 안내

  const errors = {
    name: !name.trim() ? "이름을 입력해 주세요" : NAME_RULE.test(name.trim()) ? "" : "이름은 한글 또는 영문 중 하나로만 입력해 주세요",
    email: /^\S+@\S+\.\S+$/.test(email.trim()) ? "" : "이메일 형식을 확인해 주세요",
    phone: PHONE_RULE.test(phone) ? "" : "하이픈 없이 숫자만 입력해 주세요 (예: 01012345678)",
    password: PASSWORD_RULE.test(password) ? "" : "8~15자, 영문·숫자·특수문자(!@#%^&*)를 모두 포함해 주세요",
    confirm: confirm === password ? "" : "비밀번호가 일치하지 않아요",
    agree: agree ? "" : "약관에 동의해 주세요",
  };
  const show = (k: keyof typeof errors) => (tried ? errors[k] : "");
  // 한글·영문 혼용은 "다음"을 누르기 전에도 바로 알린다 (조합 중인 낱자는 누른 뒤에만 검사)
  const nameError = nameNotice || (isMixedName(name) ? errors.name : show("name"));

  const changeName = (v: string) => {
    const cleaned = cleanName(v);
    setName(cleaned.name);
    setNameNotice(cleaned.notice);
  };

  const next = (e: FormEvent) => {
    e.preventDefault();
    setTried(true);
    if (Object.values(errors).some(Boolean)) return;
    saveSignupDraft({name: name.trim(), email: email.trim(), phone, password});
    router.push("/pin-setup");
  };

  return (
      <>
        <Header title="회원가입" right="1 / 2"/>
        <form className="space-y-3 px-5" onSubmit={next} noValidate>
          <Field label="이름" value={name} onChange={changeName} autoComplete="name" maxLength={100}
                 hint="한글 또는 영문 (띄어쓰기는 영문 이름만)" error={nameError} help={NAME_HELP}/>
          <Field label="이메일" type="email" value={email} onChange={setEmail} autoComplete="email"
                 inputMode="email" error={show("email")}/>
          <Field label="전화번호" value={phone}
                 onChange={(v) => setPhone(v.replace(/\D/g, "").slice(0, 11))} inputMode="numeric"
                 autoComplete="tel" placeholder="01012345678" hint="하이픈 없이 숫자만 입력"
                 error={show("phone")}/>
          <Field label="비밀번호" type="password" value={password} onChange={setPassword}
                 autoComplete="new-password" maxLength={15} hint="8~15자 · 영문, 숫자, 특수문자(!@#%^&*) 포함"
                 error={show("password")} noClipboard/>
          <Field label="비밀번호 확인" type="password" value={confirm} onChange={setConfirm}
                 autoComplete="new-password" maxLength={15} error={show("confirm")} noClipboard/>
          <label className="flex items-center gap-2 py-1 text-[13px]">
            <input type="checkbox" className="h-4 w-4" checked={agree}
                   onChange={(e) => setAgree(e.target.checked)}/> 이용약관 · 개인정보 수집 동의
          </label>
          {show("agree") &&
              <p role="alert" className="text-[12px] font-medium text-red-600">{show("agree")}</p>}
          <button type="submit" className="btn btn-primary">다음 · 간편비밀번호 설정</button>
        </form>
        <p className="mt-auto py-6 text-center text-[13px] text-zinc-500">
          이미 계정이 있으신가요? <Link href="/login" className="font-semibold text-zinc-900">로그인 →</Link>
        </p>
      </>
  );
}
