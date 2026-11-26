"use client";

import Header from "@/components/Header";
import PinVerifyPad from "@/components/PinVerifyPad";
import { markPinVerified } from "@/lib/auth";

// 점주 로그인 뒤의 간편비밀번호 확인. 통과하면 QR 생성 화면으로 간다(가맹점이 없으면 거기서 등록 화면으로 보낸다)
export default function MerchantPinPage() {
  return (
    <>
      <Header title="간편비밀번호 입력" />
      <PinVerifyPad doneHref="/merchant/qr" lockedHref="/merchant/locked" onVerified={markPinVerified} />
    </>
  );
}
