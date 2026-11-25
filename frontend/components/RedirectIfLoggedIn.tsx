"use client";

import { useRouter } from "next/navigation";
import { Fragment, useEffect, useLayoutEffect, useState, useSyncExternalStore } from "react";
import { type Audience, canEnter, getSession, HOME_PATH } from "@/lib/auth";

const subscribe = (onChange: () => void) => {
  window.addEventListener("storage", onChange);
  return () => window.removeEventListener("storage", onChange);
};

// 만료된 세션은 getSession 이 지우고 null 을 돌려준다
const loggedInTo = (audience: Audience) => {
  const session = getSession();
  return session !== null && canEnter(session.role, audience);
};

// 로그인 화면을 감싼다. 이미 이 앱에 로그인해 있으면 로그인 폼 대신 첫 화면으로 보낸다
export default function RedirectIfLoggedIn({ audience, children }: { audience: Audience; children: React.ReactNode }) {
  const router = useRouter();
  const loggedIn = useSyncExternalStore(subscribe, () => loggedInTo(audience), () => false);
  // Next.js(cacheComponents)는 벗어난 화면의 상태를 보존한다. 벗어날 때마다 아래를 새로 만들어
  // 로그아웃 뒤에 이전에 입력한 이메일·비밀번호가 남지 않게 한다
  const [visit, setVisit] = useState(0);
  useLayoutEffect(() => () => setVisit((v) => v + 1), []);

  // 화면에 들어올 때(다시 보일 때 포함)와 다른 탭에서 로그인했을 때 확인한다.
  // 뒤로 가기로 로그인 화면에 되돌아오지 않게 replace 로 바꾼다
  useEffect(() => {
    if (loggedInTo(audience)) router.replace(HOME_PATH[audience]);
  }, [audience, router, loggedIn]);

  return loggedIn ? null : <Fragment key={visit}>{children}</Fragment>;
}
