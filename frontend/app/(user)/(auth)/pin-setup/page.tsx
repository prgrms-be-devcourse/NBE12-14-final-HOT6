import Header from "@/components/Header";
import PinPad from "@/components/PinPad";

export default function PinSetupPage() {
  return (
    <>
      <Header title="간편비밀번호 설정" right="2 / 2" />
      <PinPad mode="set" doneHref="/login" />
    </>
  );
}
