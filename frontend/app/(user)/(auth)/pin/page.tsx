import { Suspense } from "react";
import Header from "@/components/Header";
import PinVerifyPad from "@/components/PinVerifyPad";
import { safeNext } from "@/lib/format";

async function PinBody({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  return <PinVerifyPad doneHref={safeNext(next, "/wallet")} />;
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
