"use client";

import { useState } from "react";

type Props = {
  label: string;
  message: string;
  doneLabel?: string;
  onConfirm?: () => void | Promise<void>;
  className?: string;
};

// 되돌리기 어려운 동작은 한 번 더 확인한 뒤 실행한다 (인라인 2단계)
export default function ConfirmButton({ label, message, doneLabel, onConfirm, className = "btn btn-danger btn-sm !w-auto" }: Props) {
  const [step, setStep] = useState<"idle" | "ask" | "done">("idle");
  const [error, setError] = useState("");
  if (step === "done" && doneLabel) return <span className="badge badge-bad !px-3 !py-1.5 !text-xs">{doneLabel}</span>;
  if (step === "ask")
    return (
      <span role="alertdialog" aria-label={label} className="flex flex-wrap items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2">
        <span className="text-[13px] font-medium text-red-700">{message}</span>
        <button type="button" onClick={() => setStep("idle")} className="btn btn-outline btn-sm !w-auto">취소</button>
        <button
          type="button"
          onClick={async () => {
            try {
              await onConfirm?.();
              setError("");
              setStep("done");
            } catch (e) {
              setError(e instanceof Error ? e.message : "처리하지 못했어요");
              setStep("idle");
            }
          }}
          className="btn btn-sm !w-auto !bg-red-600 !text-white hover:!bg-red-700"
        >{label}</button>
      </span>
    );
  return (
    <span className="inline-flex flex-col items-start gap-1">
      <button type="button" onClick={() => setStep("ask")} className={className}>{label}</button>
      {error && <span role="alert" className="text-[12px] font-medium text-red-600">{error}</span>}
    </span>
  );
}
