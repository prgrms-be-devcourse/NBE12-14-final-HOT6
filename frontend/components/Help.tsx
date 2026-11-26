import type { ReactNode } from "react";

// 글자 옆의 물음표. 누를 때마다 같은 id 의 안내창(HelpTip)을 열고 닫는다
export function HelpButton({ open, onClick, controls, label }: { open: boolean; onClick: () => void; controls: string; label: string }) {
  return (
    <button type="button" onClick={onClick} aria-expanded={open} aria-controls={controls} aria-label={label} className={`flex h-[18px] w-[18px] shrink-0 items-center justify-center rounded-full border border-slate-300 text-[11px] font-bold leading-none text-slate-500 ${open ? "bg-slate-100" : "bg-white"}`}>
      ?
    </button>
  );
}

export function HelpTip({ id, open, className = "", children }: { id: string; open: boolean; className?: string; children: ReactNode }) {
  return (
    <p id={id} hidden={!open} className={`rounded-xl border-2 border-dashed border-slate-400 bg-brand-600/[0.08] px-3 py-2 text-[11px] leading-normal text-slate-600 ${className}`}>
      {children}
    </p>
  );
}
