import { Suspense } from "react";
import Header from "@/components/Header";
import PinPauseNotice from "@/components/PinPauseNotice";
import PinVerifyPad from "@/components/PinVerifyPad";
import { safeNext } from "@/lib/format";

async function PinBody({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  const doneHref = safeNext(next, "/wallet");
  return (
    <>
      {/* 결제 비밀번호일 때만 적립 중단을 알린다 */}
      {doneHref === "/pay/receipt" && <PinPauseNotice />}
      <PinVerifyPad doneHref={doneHref} />
    </>
  );
}

export default function PinPage({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  return (
    <>
      <Header title="간편비밀번호 입력" />
      <Suspense fallback={null}>
        <PinBody searchParams={searchParams} />
      </Suspense>
    </>
  );
}
