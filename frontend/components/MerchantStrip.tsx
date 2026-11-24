"use client";

import { useEffect, useState } from "react";
import { getMyMerchant } from "@/lib/endpoints";

// 사장님 앱 상단: 지금 어느 가맹점으로 보고 있는지
export default function MerchantStrip() {
  const [name, setName] = useState("내 가맹점");
  const [id, setId] = useState<number | null>(null);
  useEffect(() => {
    let alive = true;
    getMyMerchant()
      .then((m) => {
        if (!alive) return;
        setName(m.name);
        setId(m.merchantId);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  return (
    <div className="flex items-center justify-between border-b border-slate-200 bg-brand-50 px-5 py-2 text-[13px]">
      <span className="font-bold text-brand-900">{name}</span>
      <span className="flex items-center gap-2 text-brand-700">
        {id !== null && <span className="text-xs">가맹점 #{id}</span>}
      </span>
    </div>
  );
}
