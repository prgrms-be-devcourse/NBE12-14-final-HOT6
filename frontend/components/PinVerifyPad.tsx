"use client";

import PinPad, { type PinResult } from "@/components/PinPad";
import { ApiError } from "@/lib/api";
import { holdPin } from "@/lib/pinStore";
import { verifyPin } from "@/lib/userApi";

// 간편비밀번호를 서버로 검증한다. 맞으면 onVerified 를 부른다(기본: 결제 요청에 실어 보낼 수 있게 메모리에 잠시 둔다).
// 틀리면 서버 메시지(남은 횟수 포함)를 보여 주고, 5회 실패로 잠기면 잠금 화면으로 보낸다.
async function verify(pin: string, onVerified: (pin: string) => void): Promise<PinResult> {
  try {
    await verifyPin(pin);
    onVerified(pin);
    return { ok: true };
  } catch (e) {
    if (e instanceof ApiError && e.code === "AUTH-006") return { ok: false, message: e.message, locked: true };
    return { ok: false, message: e instanceof ApiError ? e.message : "확인하지 못했어요. 잠시 뒤 다시 시도해 주세요" };
  }
}

type Props = { doneHref: string; lockedHref?: string; onVerified?: (pin: string) => void };

export default function PinVerifyPad({ doneHref, lockedHref, onVerified = holdPin }: Props) {
  return <PinPad mode="enter" doneHref={doneHref} lockedHref={lockedHref} onSubmit={(pin) => verify(pin, onVerified)} />;
}
