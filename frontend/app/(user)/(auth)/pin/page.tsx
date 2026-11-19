import { Suspense } from "react";
import Header from "@/components/Header";
import PinPad from "@/components/PinPad";
import { safeNext } from "@/lib/format";

async function PinBody({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  const { next } = await searchParams;
  return <PinPad mode="enter" doneHref={safeNext(next, "/wallet")} />;
}

export default function PinPage({ searchParams }: { searchParams: Promise<{ next?: string }> }) {
  return (
    <>
      <Header title="간편비밀번호 입력" right="로그인 · 결제 시 공통" />
      <Suspense fallback={null}>
        <PinBody searchParams={searchParams} />
      </Suspense>
      <div className="card-dash m-5 mt-auto">
        <ul className="space-y-0.5">
          <li>• 5회 연속 실패 시 잠금 → 이메일 인증으로 해제</li>
          <li>• 입력 성공 시 다음 화면으로 이동</li>
        </ul>
      </div>
    </>
  );
}
