"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api";
import { getMyMerchant } from "@/lib/endpoints";
import type { MerchantStatus } from "@/lib/types";

// 사장님 앱 상단: 지금 어느 가맹점으로 보고 있는지와 해지 상태. 아직 가맹점이 없는 점주는 등록 화면으로 보낸다
export default function MerchantStrip() {
  const router = useRouter();
  const [name, setName] = useState("내 가맹점");
  const [id, setId] = useState<number | null>(null);
  const [status, setStatus] = useState<MerchantStatus>("ACTIVE");
  useEffect(() => {
    let alive = true;
    getMyMerchant()
      .then((m) => {
        if (!alive) return;
        setName(m.name);
        setId(m.merchantId);
        setStatus(m.status);
      })
      .catch((e: unknown) => {
        if (alive && e instanceof ApiError && e.code === "MER-001") router.replace("/merchant/register");
      });
    return () => {
      alive = false;
    };
  }, [router]);
  return (
    <div className="flex items-center justify-between border-b border-slate-200 bg-brand-50 px-5 py-2 text-[13px]">
      <span className="font-bold text-brand-900">{name}</span>
      <span className="flex items-center gap-2 text-brand-700">
        {status !== "ACTIVE" && <span className={`badge ${status === "CLOSING" ? "badge-warn" : "badge-bad"}`}>{status === "CLOSING" ? "해지 진행 중" : "해지 완료"}</span>}
        {id !== null && <span className="text-xs">가맹점 #{id}</span>}
      </span>
    </div>
  );
}
