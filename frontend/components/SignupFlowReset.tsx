"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import { resetSignupFlow } from "@/lib/signupDraft";

// 같은 가입의 두 화면 사이를 오가는 동안에만 입력값을 남긴다. 이용자 가입과 점주 가입은 서로 값을 넘기지 않는다
const SIGNUP_FLOWS = [["/signup", "/pin-setup"], ["/merchant/signup", "/merchant/signup/pin"]];

// 어느 화면으로 나가든 알 수 있도록 최상위 레이아웃에 둔다. 화면에는 아무것도 그리지 않는다
export default function SignupFlowReset() {
  const pathname = usePathname();
  const previous = useRef(-1); // 직전 화면이 속한 가입 흐름(-1 은 가입 화면이 아님)
  useEffect(() => {
    const current = SIGNUP_FLOWS.findIndex((paths) => paths.includes(pathname));
    if (current === -1 || (previous.current !== -1 && previous.current !== current)) resetSignupFlow();
    previous.current = current;
  }, [pathname]);
  return null;
}
