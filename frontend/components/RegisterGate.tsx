"use client";

import Link from "next/link";
import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { getSession, parseSession, readRawSession } from "@/lib/auth";

const subscribe = (onChange: () => void) => {
  window.addEventListener("storage", onChange);
  return () => window.removeEventListener("storage", onChange);
};
const subscribeNever = () => () => {};

// 가맹점 등록은 로그인한 이용자(USER)만 할 수 있다. 서버가 토큰으로 회원을 정하기 때문이다.
// - 로그인하지 않았으면 이용자 로그인으로 보낸다(로그인과 간편비밀번호 확인 뒤 이 화면으로 돌아온다)
// - 이미 점주(OWNER)면 이미 가맹점이 있다는 안내, 운영자는 등록할 수 없다는 안내를 보여 준다
export default function RegisterGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const mounted = useSyncExternalStore(subscribeNever, () => true, () => false);
  const raw = useSyncExternalStore(subscribe, readRawSession, () => null);
  const session = parseSession(raw);

  useEffect(() => {
    if (!mounted) return;
    if (!getSession()) router.replace("/login?next=/merchant/register"); // 만료된 세션은 getSession 이 지운다
  }, [mounted, router]);

  if (!mounted || !session) return null;
  if (session.role === "OWNER") {
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
  if (session.role === "ADMIN") {
    return (
      <div className="space-y-3 px-5 pt-6">
        <section className="card text-center">
          <div className="text-lg font-bold">운영자 계정으로는 등록할 수 없어요</div>
          <p className="note mt-2">가맹점 등록은 이용자 계정으로 로그인해서 진행해 주세요.</p>
        </section>
        <Link href="/login?next=/merchant/register" className="btn btn-outline">이용자로 로그인</Link>
      </div>
    );
  }
  return <>{children}</>;
}
