"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";

// ok=false 이면 message 를 보여 주고, locked 면 잠금 화면으로 보낸다
export type PinResult = { ok: true } | { ok: false; message: string; locked?: boolean };
type Props = {
  mode: "set" | "enter";
  doneHref: string;
  lockedHref?: string;
  // 6자리를 다 입력했을 때(set 은 두 번 일치했을 때) 서버 호출을 맡긴다. 없으면 바로 이동한다
  onSubmit?: (pin: string) => Promise<PinResult>;
  setGuide?: string; // set 에서 처음 입력할 때의 안내를 바꿀 때
};

const KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"];

export default function PinPad({ mode, doneHref, lockedHref = "/locked", onSubmit, setGuide = "결제·로그인에 쓸 6자리를 입력하세요" }: Props) {
  const router = useRouter();
  const [pin, setPin] = useState("");
  const [first, setFirst] = useState<string | null>(null);
  const [requesting, setBusy] = useState(false);
  const [moving, startTransition] = useTransition();
  const busy = requesting || moving;
  const [error, setError] = useState("");

  const submit = async (value: string) => {
    if (!onSubmit) return router.push(doneHref);
    setBusy(true);
    const res = await onSubmit(value);
    // 화면이 이동한 뒤에도 이 컴포넌트는 메모리에 남을 수 있어서(되돌아오면 그대로 보임) busy 를 반드시 푼다.
    // 이동 중에는 pending 으로 "확인하는 중" 을 이어서 보여 준다.
    setBusy(false);
    if (res.ok) return startTransition(() => router.push(doneHref));
    if (res.locked) return startTransition(() => router.push(lockedHref));
    if (mode === "set") setFirst(null);
    setError(res.message);
  };

  const press = (k: string) => {
    if (busy) return;
    if (k === "⌫") return setPin((p) => p.slice(0, -1));
    if (!k || pin.length >= 6) return;
    setError("");
    const next = pin + k;
    setPin(next);
    if (next.length < 6) return;

    setTimeout(() => {
      setPin("");
      if (mode === "set") {
        if (first === null) return setFirst(next);
        if (first === next) return void submit(next);
        setFirst(null);
        return setError("처음 입력과 일치하지 않습니다. 다시 설정해 주세요");
      }
      void submit(next);
    }, 150);
  };

  const guide =
    mode === "set"
      ? first === null
        ? setGuide
        : "확인을 위해 한 번 더 입력하세요"
      : "간편비밀번호 6자리를 입력하세요";

  return (
    <div className="px-5">
      <p className="mt-2 text-center text-sm text-zinc-600">{guide}</p>
      <div className="my-6 flex justify-center gap-3" aria-label={`${pin.length}자리 입력됨`}>
        {Array.from({ length: 6 }, (_, i) => (
          <span
            key={i}
            className={`h-3.5 w-3.5 rounded-full border-2 border-brand-600 ${i < pin.length ? "bg-brand-600" : ""}`}
          />
        ))}
      </div>
      <p className="mb-4 min-h-5 text-center text-[13px] font-medium text-red-700" role="alert">
        {error && `! ${error}`}
      </p>
      <div className="grid grid-cols-3 gap-2.5">
        {KEYS.map((k, i) => (
          <button
            key={i}
            type="button"
            onClick={() => press(k)}
            disabled={!k || busy}
            className="rounded-xl border border-zinc-300 bg-white py-4 text-xl font-medium shadow-sm active:bg-brand-50 disabled:invisible"
          >
            {k}
          </button>
        ))}
      </div>
      {busy && <p className="note mt-4 text-center" role="status">확인하는 중이에요…</p>}
    </div>
  );
}
