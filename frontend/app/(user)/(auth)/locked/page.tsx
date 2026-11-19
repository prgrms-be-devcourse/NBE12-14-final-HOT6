import Link from "next/link";
import Countdown from "@/components/Countdown";
import Field from "@/components/Field";
import { user } from "@/lib/mock";

export default function LockedPage() {
  return (
    <div className="flex flex-1 flex-col px-5 pt-14">
      <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-full border-2 border-red-700 text-2xl font-bold text-red-700">
        !
      </div>
      <h1 className="text-center text-xl font-medium">계정이 잠겼습니다</h1>
      <p className="mt-1 text-center text-[13px] text-zinc-500">간편비밀번호를 5회 연속 틀렸습니다</p>
      <div className="mt-6 space-y-3">
        <div className="card-gray flex items-center justify-between">
          <div>
            <div className="label !mb-0">인증 이메일</div>
            <div className="font-medium">{user.maskedEmail}</div>
          </div>
          <button type="button" className="btn btn-outline btn-sm !w-auto">인증번호 받기</button>
        </div>
        <Field label="인증번호 6자리" placeholder="000000" />
        <p className="note">
          남은 시간 <Countdown seconds={272} className="font-semibold text-zinc-900" />
        </p>
        <Link href="/pin-setup" className="btn btn-primary">잠금 해제</Link>
        <p className="note text-center">해제 후 간편비밀번호를 다시 설정합니다</p>
      </div>
    </div>
  );
}
