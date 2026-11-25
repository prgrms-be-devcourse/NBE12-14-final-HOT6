import CashbackHistory from "@/components/CashbackHistory";

// 하단 캐시백 탭. 홈의 캐시백 잔액에서 들어가는 캐시백 내역 위에 이번 달 적립 한도를 더 보여 준다
export default function CashbackPage() {
  return <CashbackHistory showLimit />;
}
