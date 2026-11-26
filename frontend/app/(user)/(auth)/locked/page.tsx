import Link from "next/link";
import HomeLink from "@/components/HomeLink";

// 5회 연속 틀리면 잠기고, 잠긴 지 10분이 지나면 서버가 자동으로 풀어 준다 (이메일 인증 해제는 MVP 이후)
export default function LockedPage() {
  return (
    <div className="flex flex-1 flex-col px-5 pt-14">
      <HomeLink />
      <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full border-2 border-red-700 text-2xl font-bold text-red-700">
        !
      </div>
      <h1 className="text-center text-xl font-medium">간편비밀번호가 잠겼습니다</h1>
      <p className="mt-1 text-center text-[13px] text-zinc-500">5회 연속 틀려서 잠겼어요</p>
      <div className="mt-6 space-y-3">
        <div className="card-gray">
          <div className="font-medium">잠긴 지 10분이 지나면 다시 시도할 수 있어요</div>
          <p className="note mt-1">그동안은 결제를 할 수 없어요. 지갑과 내역은 그대로 볼 수 있어요.</p>
        </div>
        <Link href="/wallet" className="btn btn-primary">내 지갑으로 돌아가기</Link>
      </div>
    </div>
  );
}
