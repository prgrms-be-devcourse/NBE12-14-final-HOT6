"use client";

import { useRouter } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";
import { getSession, parseSession, readRawSession } from "@/lib/auth";

const subscribe = (onChange: () => void) => {
  window.addEventListener("storage", onChange);
  return () => window.removeEventListener("storage", onChange);
};
const subscribeNever = () => () => {};

const needsPin = (session: ReturnType<typeof parseSession>) => session?.role === "OWNER" && !session.pinVerified;

// 점주가 간편비밀번호 확인을 통과하지 않았으면 확인 화면으로 보낸다. 화면 단계의 확인이며, 접근 통제는 서버가 토큰으로 판단한다
export default function OwnerPinGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const mounted = useSyncExternalStore(subscribeNever, () => true, () => false);
  const raw = useSyncExternalStore(subscribe, readRawSession, () => null);
  const blocked = needsPin(parseSession(raw));

  // 보존됐던 화면이 다시 보일 때 이전 값으로 판단하지 않도록, 이동은 저장된 세션을 새로 읽어 정한다
  useEffect(() => {
    if (needsPin(getSession())) router.replace("/merchant/pin");
  }, [blocked, router]);

  return mounted && !blocked ? <>{children}</> : null;
}
