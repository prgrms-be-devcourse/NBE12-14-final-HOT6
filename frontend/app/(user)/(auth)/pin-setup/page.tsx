import Header from "@/components/Header";
import PinPad from "@/components/PinPad";

export default function PinSetupPage() {
  return (
    <>
      <Header title="간편비밀번호 설정" right="2 / 2" />
      <PinPad mode="set" doneHref="/login" />
      <div className="card-dash m-5 mt-auto">
        <div className="mb-1 font-semibold">설정 · 확인 2단계</div>
        <ul className="space-y-0.5">
          <li>• 5회 연속 실패 시 잠금</li>
          <li>• 설정 후 마이페이지에서 변경 가능</li>
        </ul>
      </div>
    </>
  );
}
