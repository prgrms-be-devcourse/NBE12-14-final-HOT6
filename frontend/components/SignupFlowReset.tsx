"use client";

import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { resetSignupFlow } from "@/lib/signupDraft";

// 회원가입 두 화면 사이를 오가는 동안에만 입력값을 남긴다
const SIGNUP_PATHS = ["/signup", "/pin-setup"];

// 어느 화면으로 나가든 알 수 있도록 최상위 레이아웃에 둔다. 화면에는 아무것도 그리지 않는다
export default function SignupFlowReset() {
  const pathname = usePathname();
  useEffect(() => {
    if (!SIGNUP_PATHS.includes(pathname)) resetSignupFlow();
  }, [pathname]);
  return null;
}
