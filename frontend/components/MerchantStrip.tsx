"use client";

import { useEffect, useState } from "react";
import { loadCtx } from "@/lib/devContext";
import { merchant } from "@/lib/mock";

// 사장님 앱 상단: 지금 어느 가맹점으로 보고 있는지
export default function MerchantStrip() {
  const [name, setName] = useState<string>(merchant.name);
  const [id, setId] = useState<number | null>(null);
  useEffect(() => {
    const c = loadCtx();
    if (c.merchantName) setName(c.merchantName);
    setId(c.merchantId);
  }, []);
  return (
    <div className="flex items-center justify-between bg-emerald-50 px-5 py-2 text-[13px]">
      <span className="font-semibold text-emerald-900">{name}</span>
      <span className="flex items-center gap-2 text-emerald-800">
        {id !== null && <span className="text-xs">가맹점 #{id}</span>}
        <span className="badge badge-ok">운영중</span>
      </span>
    </div>
  );
}
