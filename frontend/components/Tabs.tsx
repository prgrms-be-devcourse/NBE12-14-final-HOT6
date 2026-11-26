"use client";

import { useRouter, useSearchParams } from "next/navigation";

export type TabItem<T extends string> = { id: T; label: string };

// 주소의 ?tab= 으로 선택한 탭을 기억한다(다른 화면에서 특정 탭으로 바로 올 수 있다). 모르는 값이면 첫 탭이다.
// useSearchParams 를 쓰므로 Suspense 안에서 불러야 한다
export function useTab<T extends string>(items: TabItem<T>[]): [T, (id: T) => void] {
  const router = useRouter();
  const param = useSearchParams().get("tab");
  const active = items.find((i) => i.id === param)?.id ?? items[0].id;
  return [active, (id: T) => router.replace(`?tab=${id}`, { scroll: false })];
}

type Props<T extends string> = { items: TabItem<T>[]; active: T; onChange: (id: T) => void; label: string };

// 화면 안의 묶음을 바꿔 보는 탭 줄
export default function Tabs<T extends string>({ items, active, onChange, label }: Props<T>) {
  return (
    <div role="tablist" aria-label={label} className="flex flex-wrap gap-1 border-b border-slate-200">
      {items.map((i) => (
        <button
          key={i.id}
          type="button"
          role="tab"
          aria-selected={active === i.id}
          onClick={() => onChange(i.id)}
          className={`-mb-px border-b-2 px-4 py-2.5 text-sm transition ${active === i.id ? "border-brand-600 font-bold text-brand-600" : "border-transparent font-medium text-slate-500 hover:text-slate-800"}`}
        >
          {i.label}
        </button>
      ))}
    </div>
  );
}
