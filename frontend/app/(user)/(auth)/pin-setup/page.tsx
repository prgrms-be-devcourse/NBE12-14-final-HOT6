"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Header from "@/components/Header";
import PinPad, { type PinResult } from "@/components/PinPad";
import { ApiError } from "@/lib/api";
import { clearSignupDraft, loadSignupDraft, saveSignupDraft } from "@/lib/signupDraft";
import { signup } from "@/lib/userApi";

// 회원가입 2단계: 간편비밀번호를 두 번 입력하면 1단계 정보와 함께 가입을 요청한다.
// 가입 중이 아니면(마이페이지의 변경 등) 설정만 하고 이동한다 — 변경 API 가 생기면 여기서 연결한다.
export default function PinSetupPage() {
  const router = useRouter();
  const [draft] = useState(() => loadSignupDraft());

  const submit = async (pin: string): Promise<PinResult> => {
    if (!draft) return { ok: true };
    try {
      await signup({ name: draft.name, email: draft.email, phone: draft.phone, password: draft.password, pin });
      clearSignupDraft();
      return { ok: true };
    } catch (e) {
      // 이메일·전화번호 중복은 1단계에서 고쳐야 하므로 사유를 들고 돌아간다
      if (e instanceof ApiError && (e.code === "AUTH-001" || e.code === "AUTH-002")) {
        saveSignupDraft({ ...draft, error: e.message });
        router.replace("/signup");
        return { ok: false, message: e.message };
      }
      return { ok: false, message: e instanceof ApiError ? e.message : "가입하지 못했어요. 잠시 뒤 다시 시도해 주세요" };
    }
  };

  return (
    <>
      <Header title="간편비밀번호 설정" right={draft ? "2 / 2" : undefined} back={draft ? "/signup" : undefined} />
      <PinPad mode="set" doneHref="/login" onSubmit={draft ? submit : undefined} />
    </>
  );
}
