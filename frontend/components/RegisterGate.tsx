"use client";

import Link from "next/link";
import { useEffect, useState, useSyncExternalStore } from "react";
import { ApiError } from "@/lib/api";
import { getSession, parseSession, readRawSession } from "@/lib/auth";
import { getMyMerchant } from "@/lib/endpoints";
import OwnerPinGate from "@/components/OwnerPinGate";

const subscribe = (onChange: () => void) => {
  window.addEventListener("storage", onChange);
  return () => window.removeEventListener("storage", onChange);
};
const subscribeNever = () => () => {};

// 가맹점 등록은 점주 계정만 할 수 있다. 서버가 토큰으로 회원을 정하기 때문에 로그인이 필요하다.
// - 로그인하지 않았거나 점주가 아닌 계정이면 점주 로그인과 점주 회원가입을 안내한다
// - 점주는 간편비밀번호 확인을 통과하고 가맹점이 없을 때만 등록할 수 있다
export default function RegisterGate({ children }: { children: React.ReactNode }) {
  const mounted = useSyncExternalStore(subscribeNever, () => true, () => false);
  const raw = useSyncExternalStore(subscribe, readRawSession, () => null);
  const loggedIn = useSyncExternalStore(subscribe, () => getSession() !== null, () => false); // 만료된 세션은 getSession 이 지운다
  const session = parseSession(raw);

  if (!mounted) return null;
  if (!loggedIn || !session) {
    return (
      <div className="space-y-3 px-5 pt-6">
        <section className="card text-center">
          <div className="text-lg font-bold">로그인하세요</div>
          <p className="note mt-2">가맹점 등록은 점주 계정으로 로그인한 뒤에 할 수 있어요. 계정이 없다면 먼저 점주 회원가입을 해 주세요.</p>
        </section>
        <Link href="/merchant/login" className="btn btn-primary">로그인하러 가기</Link>
        <Link href="/merchant/signup" className="btn btn-outline">점주 회원가입</Link>
      </div>
    );
  }
  if (session.role !== "OWNER") {
    return (
      <div className="space-y-3 px-5 pt-6">
        <section className="card text-center">
          <div className="text-lg font-bold">점주 계정이 필요해요</div>
          <p className="note mt-2">지금 로그인한 계정으로는 가맹점을 등록할 수 없어요. 점주 계정으로 로그인하거나 점주 회원가입을 해 주세요.</p>
        </section>
        <Link href="/merchant/login" className="btn btn-primary">가맹점주 로그인</Link>
        <Link href="/merchant/signup" className="btn btn-outline">점주 회원가입</Link>
      </div>
    );
  }
  return (
    <OwnerPinGate>
      <OwnerGate key={session.userId}>{children}</OwnerGate>
    </OwnerPinGate>
  );
}

// 점주 한 명은 가맹점 하나: 이미 있으면 안내하고, 없으면 등록 화면을 보여 준다
function OwnerGate({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<"loading" | "none" | "exists" | "closed" | "error">("loading");

  useEffect(() => {
    let alive = true;
    getMyMerchant().then(
      (m) => alive && setState(m.status === "ACTIVE" ? "exists" : "closed"),
      (e: unknown) => alive && setState(e instanceof ApiError && e.code === "MER-001" ? "none" : "error"),
    );
    return () => { alive = false; };
  }, []);

  if (state === "loading") return null;
  if (state === "none") return <>{children}</>;
  if (state === "closed") {
    return (
      <div className="space-y-3 px-5 pt-6">
        <section className="card text-center">
          <div className="text-lg font-bold">해지한 가맹점이 있어요</div>
          <p className="note mt-2">점주 한 명은 가맹점을 하나만 등록할 수 있어서, 해지한 뒤에는 다시 등록할 수 없어요.</p>
        </section>
        <Link href="/merchant/daily" className="btn btn-outline">정산 보러 가기</Link>
      </div>
    );
  }
  if (state === "error") {
    return <p role="alert" className="mx-5 mt-6 rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-800">가맹점 정보를 확인하지 못했어요. 잠시 뒤 다시 열어 주세요.</p>;
  }
  return (
    <div className="space-y-3 px-5 pt-6">
      <section className="card text-center">
        <div className="text-lg font-bold">이미 가맹점이 있어요</div>
        <p className="note mt-2">점주 한 명은 가맹점을 하나만 등록할 수 있어요.</p>
      </section>
      <Link href="/merchant/qr" className="btn btn-primary">QR 생성하러 가기</Link>
    </div>
  );
}
