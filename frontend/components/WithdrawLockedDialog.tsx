"use client";

import AlertDialog from "@/components/AlertDialog";

// 탈퇴하다가 간편비밀번호가 잠긴 경우의 안내. 이메일 인증으로 바꾸는 기능은 아직 없고, 지금은 잠금 시간이 지나면 풀린다
export default function WithdrawLockedDialog({ onConfirm }: { onConfirm: () => void }) {
  return (
    <AlertDialog
      title="탈퇴할 수 없어요"
      message="핀 번호 입력 횟수가 5회 초과입니다. 이메일 인증을 통해 PIN 번호 수정 후 다시 시도하십시오."
      onConfirm={onConfirm}
    />
  );
}
