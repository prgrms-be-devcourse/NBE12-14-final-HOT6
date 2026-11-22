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
    /* eslint-disable react-hooks/set-state-in-effect -- 브라우저 저장소 값은 마운트 후에 읽는다 */
    if (c.merchantName) setName(c.merchantName);
    setId(c.merchantId);
    /* eslint-enable react-hooks/set-state-in-effect */
  }, []);
  return (
    <div className="flex items-center justify-between border-b border-slate-200 bg-brand-50 px-5 py-2 text-[13px]">
      <span className="font-bold text-brand-900">{name}</span>
      <span className="flex items-center gap-2 text-brand-700">
        {id !== null && <span className="text-xs">가맹점 #{id}</span>}
        <span className="badge badge-ok">운영중</span>
      </span>
    </div>
  );
}
