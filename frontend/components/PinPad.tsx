"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

type Props = { mode: "set" | "enter"; doneHref: string; lockedHref?: string };

const KEYS = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"];
const MAX_FAIL = 5;

export default function PinPad({ mode, doneHref, lockedHref = "/locked" }: Props) {
  const router = useRouter();
  const [pin, setPin] = useState("");
  const [first, setFirst] = useState<string | null>(null);
  const [fails, setFails] = useState(0);
  const [error, setError] = useState("");

  const press = (k: string) => {
    if (k === "⌫") return setPin((p) => p.slice(0, -1));
    if (!k || pin.length >= 6) return;
    const next = pin + k;
    setPin(next);
    if (next.length < 6) return;

    setTimeout(() => {
      setPin("");
      if (mode === "set") {
        if (first === null) return setFirst(next);
        if (first === next) return router.push(doneHref);
        setFirst(null);
        return setError("처음 입력과 일치하지 않습니다. 다시 설정해 주세요");
      }
      // 데모: 000000 은 항상 실패
      if (next !== "000000") return router.push(doneHref);
      const f = fails + 1;
      setFails(f);
      if (f >= MAX_FAIL) return router.push(lockedHref);
      setError(`일치하지 않습니다 (실패 ${f} / ${MAX_FAIL})`);
    }, 150);
  };

  const guide =
    mode === "set"
      ? first === null
        ? "결제·로그인에 쓸 6자리를 입력하세요"
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
      <p className="mb-4 h-5 text-center text-[13px] font-medium text-red-700" role="alert">
        {error && `! ${error}`}
      </p>
      <div className="grid grid-cols-3 gap-2.5">
        {KEYS.map((k, i) => (
          <button
            key={i}
            type="button"
            onClick={() => press(k)}
            disabled={!k}
            className="rounded-xl border border-zinc-300 bg-white py-4 text-xl font-medium shadow-sm active:bg-brand-50 disabled:invisible"
          >
            {k}
          </button>
        ))}
      </div>
      {mode === "enter" && (
        <p className="note mt-4 text-center">데모: 000000 을 입력하면 실패 처리됩니다.</p>
      )}
    </div>
  );
}
