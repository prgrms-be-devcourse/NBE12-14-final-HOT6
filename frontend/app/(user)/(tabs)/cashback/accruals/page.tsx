"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import CashbackHistory, { CASHBACK_TITLE } from "@/components/CashbackHistory";
import Header from "@/components/Header";

// 뒤로 가기는 들어온 화면으로 돌아간다
const BACK: Record<string, string> = { wallet: "/wallet", my: "/my" };

function FromEntry() {
  const from = useSearchParams().get("from");
  return <CashbackHistory back={BACK[from ?? ""] ?? "/cashback"} />;
}

export default function CashbackAccrualsPage() {
  return (
    <Suspense fallback={<Header title={CASHBACK_TITLE} back="/cashback" />}>
      <FromEntry />
    </Suspense>
  );
}
