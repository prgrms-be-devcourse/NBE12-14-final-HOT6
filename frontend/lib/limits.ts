// 서비스 한도 (기획서 docs/project-spec.md WAL-02 + 팀 확정값). 바뀌면 여기만 고친다.
export const LIMITS = {
  monthlyCharge: 1_000_000, // 월 충전 한도
  holding: 1_000_000, // 지갑 보유 한도 (기획서)
  perPayment: 1_000_000, // 결제 한도 (1회 기준으로 적용 — 기간 기준 확인 필요)
  monthlyCashback: 30_000, // 월 캐시백 적립 한도 (현재값)
} as const;
