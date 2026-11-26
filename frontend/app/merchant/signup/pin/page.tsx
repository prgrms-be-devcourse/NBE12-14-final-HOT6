"use client";

import SignupPinStep from "@/components/SignupPinStep";
import { signupOwner } from "@/lib/userApi";

export default function MerchantSignupPinPage() {
  return (
    <SignupPinStep signupHref="/merchant/signup" doneHref="/merchant/login" request={signupOwner} guide="점주 계정에서 쓸 6자리를 입력하세요" />
  );
}
