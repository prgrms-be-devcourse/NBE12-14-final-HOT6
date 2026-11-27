"use client";

import { useRouter } from "next/navigation";
import { useLayoutEffect, useState } from "react";
import AlertDialog from "@/components/AlertDialog";
import PinPad, { type PinResult } from "@/components/PinPad";
import WithdrawLockedDialog from "@/components/WithdrawLockedDialog";
import { ApiError } from "@/lib/api";
import { logout } from "@/lib/auth";
import { holdPin } from "@/lib/pinStore";
import { isAlreadyWithdrawn, verifyPin } from "@/lib/userApi";
import { clearWithdrawal } from "@/lib/withdrawPending";

// 탈퇴할 때의 간편비밀번호 화면. 잠기면 잠금 화면 대신 안내 창을 띄우고, 이미 탈퇴한 계정이면 완료를 안내한다
export default function WithdrawPinPad() {
  const router = useRouter();
  const [dialog, setDialog] = useState<"locked" | "done" | null>(null);

  // 화면을 벗어나면 잠금 안내를 닫는다. 다시 들어왔을 때 이전 안내가 남아 있지 않게 한다 (cacheComponents)
  useLayoutEffect(() => () => setDialog((d) => (d === "locked" ? null : d)), []);

  const submit = async (pin: string): Promise<PinResult> => {
    try {
      await verifyPin(pin, true);
      holdPin(pin);
      return { ok: true };
    } catch (e) {
      if (isAlreadyWithdrawn(e)) {
        clearWithdrawal();
        setDialog("done");
        return { ok: false, message: "" };
      }
      if (e instanceof ApiError && e.code === "AUTH-006") {
        clearWithdrawal();
        setDialog("locked");
        return { ok: false, message: "" };
      }
      if (e instanceof ApiError && e.status === 401) router.replace("/login"); // 로그인이 만료됨
      return { ok: false, message: e instanceof ApiError ? e.message : "확인하지 못했어요. 잠시 뒤 다시 시도해 주세요" };
    }
  };

  return (
    <>
      <PinPad mode="enter" doneHref="/my/withdraw/done" onSubmit={submit} />
      {dialog === "locked" && <WithdrawLockedDialog onConfirm={() => { setDialog(null); router.push("/my"); }} />}
      {dialog === "done" && (
        <AlertDialog
          title="탈퇴가 완료되었어요"
          message="처리 결과를 바로 받지 못했지만 탈퇴는 끝났어요."
          confirmLabel="로그인 화면으로"
          onConfirm={() => { logout(); router.push("/login"); }}
        />
      )}
    </>
  );
}
