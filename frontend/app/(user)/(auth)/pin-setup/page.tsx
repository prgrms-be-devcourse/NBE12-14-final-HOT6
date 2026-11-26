"use client";

import Link from "next/link";
import { useLayoutEffect, useState, useSyncExternalStore } from "react";
import Header from "@/components/Header";
import PinPad, { type PinResult } from "@/components/PinPad";
import { ApiError } from "@/lib/api";
import { clearSignupDraft, getSignupFlow, loadSignupDraft, subscribeSignupFlow } from "@/lib/signupDraft";
import { signup } from "@/lib/userApi";

const NO_DRAFT = "가입 정보가 없어요. 처음부터 다시 입력해 주세요";

export default function PinSetupPage() {
  const flow = useSyncExternalStore(subscribeSignupFlow, getSignupFlow, () => 0);
  // 화면을 벗어날 때마다 새로 만든다: 입력하던 간편비밀번호와 오류·중복 안내가 남지 않게
  const [visit, setVisit] = useState(0);
  useLayoutEffect(() => () => setVisit((v) => v + 1), []);
  return <PinSetup key={`${flow}-${visit}`} />;
}

// 회원가입 2단계: 간편비밀번호를 두 번 입력하면 1단계 정보와 함께 가입을 요청한다.
function PinSetup() {
  const [hasDraft] = useState(() => loadSignupDraft() !== null);
  const [dup, setDup] = useState(false); // 이메일·전화번호 중복: 1단계 정보를 고쳐야 한다

  const submit = async (pin: string): Promise<PinResult> => {
    // 1단계에서 고친 값이 반영되도록 보내는 시점에 읽는다
    const draft = loadSignupDraft();
    if (!draft) return { ok: false, message: NO_DRAFT };
    try {
      await signup({ name: draft.name, email: draft.email, phone: draft.phone, password: draft.password, pin });
      clearSignupDraft();
      return { ok: true };
    } catch (e) {
      // 이메일·전화번호 중복은 PIN 이 아니라 1단계 정보를 고쳐야 한다. 자동으로 돌려보내지 않고 사유와 이동 버튼을 보여 준다
      if (e instanceof ApiError && (e.code === "AUTH-001" || e.code === "AUTH-002")) {
        setDup(true);
        return { ok: false, message: e.message };
      }
      return { ok: false, message: e instanceof ApiError ? e.message : "가입하지 못했어요. 잠시 뒤 다시 시도해 주세요" };
    }
  };

  // 1단계를 거치지 않았거나 가입 흐름을 벗어났다 돌아온 경우: 가입된 것처럼 넘어가지 않게 안내한다
  if (!hasDraft) {
    return (
      <>
        <Header title="간편비밀번호 설정" />
        <div className="space-y-3 px-5">
          <p role="status" className="card text-center text-sm text-slate-600">{NO_DRAFT}</p>
          <Link href="/signup" className="btn btn-primary">회원가입 하러 가기</Link>
        </div>
      </>
    );
  }

  return (
    <>
      <Header title="간편비밀번호 설정" right="2 / 2" back="/signup" />
      <PinPad mode="set" doneHref="/login" onSubmit={submit} />
      {dup && (
        <div className="mx-5 mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-3 text-center">
          <p className="text-[13px] text-red-800">가입 정보를 다른 값으로 고친 뒤 다시 시도해 주세요.</p>
          <Link href="/signup" className="btn btn-outline btn-sm mt-2">가입 정보 수정하기</Link>
        </div>
      )}
    </>
  );
}
