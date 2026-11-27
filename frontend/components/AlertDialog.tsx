"use client";

import { useId } from "react";

type Props = { title: string; message: string; confirmLabel?: string; onConfirm: () => void };

// 버튼을 눌러야만 닫히는 안내 창
export default function AlertDialog({ title, message, confirmLabel = "확인", onConfirm }: Props) {
  const id = useId();
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 px-5">
      <div role="alertdialog" aria-modal="true" aria-labelledby={`${id}-title`} aria-describedby={`${id}-message`} className="w-full max-w-[360px] rounded-2xl bg-white p-5 text-center">
        <h2 id={`${id}-title`} className="text-lg font-bold">{title}</h2>
        <p id={`${id}-message`} className="mt-2 break-keep text-[14px] leading-relaxed text-slate-700">{message}</p>
        <button type="button" autoFocus onClick={onConfirm} className="btn btn-primary mt-4">{confirmLabel}</button>
      </div>
    </div>
  );
}
