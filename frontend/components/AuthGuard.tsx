"use client";

import { Fragment, useEffect, useSyncExternalStore } from "react";
import { usePathname, useRouter } from "next/navigation";
import { type Audience, canEnter, getSession, LOGIN_PATH, parseSession, readRawSession } from "@/lib/auth";

const subscribe = (onChange: () => void) => {
  window.addEventListener("storage", onChange);
  return () => window.removeEventListener("storage", onChange);
};
const subscribeNever = () => () => {};

// 로그인하지 않았거나 이 앱에 들어올 수 없는 역할이면 로그인 화면으로 보낸다. 서버가 토큰으로 다시 판단하므로 화면 쪽은 이동만 맡는다.
// publicPaths 는 로그인 없이 볼 수 있는 경로 접두어(로그인·가입 화면)다.
export default function AuthGuard({ audience, publicPaths = [], children }: { audience: Audience; publicPaths?: string[]; children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const isPublic = publicPaths.some((p) => pathname === p || pathname.startsWith(`${p}/`));
  // 서버·하이드레이션 중에는 저장소를 읽지 않고, 브라우저에서 한 번 더 그릴 때 읽는다
  const mounted = useSyncExternalStore(subscribeNever, () => true, () => false);
  const raw = useSyncExternalStore(subscribe, readRawSession, () => null);
  const session = parseSession(raw);
  const allowed = isPublic || (session !== null && canEnter(session.role, audience));

  useEffect(() => {
    if (!mounted || isPublic) return;
    const current = getSession(); // 만료된 세션은 여기서 지워진다
    if (!current || !canEnter(current.role, audience)) router.replace(LOGIN_PATH[audience]);
  }, [mounted, isPublic, audience, pathname, router]);

  // Next.js(cacheComponents)는 화면을 벗어나도 숨겨 두어 상태가 남는다. 로그인한 사람이 바뀌면 이전 사용자의 화면 상태
  // (QR, 오류 문구 등)가 보이지 않도록 로그인·로그아웃 때마다 아래 화면을 새로 만든다.
  const sessionKey = mounted && session ? `${session.userId}-${session.expiresAt}` : "none";

  // 공개 경로는 로그인 여부와 상관없으므로 서버에서도 바로 그린다. 서버에서 null 이면 그 페이지가 렌더링에서 빠져
  // Next.js 가 즉시 이동(instant navigation)을 검증하지 못한다. 보호된 경로만 세션 확인 뒤에 보여 준다.
  return isPublic || (mounted && allowed) ? <Fragment key={sessionKey}>{children}</Fragment> : null;
}
