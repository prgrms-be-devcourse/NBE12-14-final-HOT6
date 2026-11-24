// 백엔드 DTO 와 1:1 (src/main/java/.../dto)
// 적립 대상 여부는 서버가 결정한다: revenueProof(연매출 30억 미만 증빙 서류)가 있으면 자동 승인
export type MerchantRegisterRequest = {
  name: string;
  address: string;
  revenueProof?: string;
};
export type MerchantRegisterResponse = { merchantId: number; name: string; address: string; cashbackEligible: boolean };
export type MerchantResponse = MerchantRegisterResponse;
export type MerchantUpdateRequest = { name?: string; address?: string };

// 정산. 날짜는 yyyy-MM-dd, 월은 yyyy-MM
export type DailySettlementResponse = {
  settlementDate: string;
  paymentTotal: number;
  cancelTotal: number;
  netSales: number;
  carryIn: number;
  confirmedAmount: number;
  carryOut: number;
};
export type MonthlySettlementStatus = "PENDING" | "PAID";
export type MonthlySettlementResponse = {
  settlementMonth: string;
  payoutAmount: number;
  status: MonthlySettlementStatus;
  paidAt: string | null;
};
export type MerchantAccountResponse = { balance: number };
export type SettlementRunResult = { date: string; created: number; skipped: number; failed: number };
export type MonthlySettlementRunResult = { month: string; created: number; paid: number; failed: number };

// 원장 대사
export type ReconciliationType =
  | "ACCOUNT_BALANCE"
  | "DEVMONEY_BALANCE"
  | "CASHBACK_BALANCE"
  | "CASHBACK_EARNING"
  | "CASHBACK_BUDGET"
  | "CHARGE_TRANSFER"
  | "REFUND_TRANSFER"
  | "DAILY_NET_SALES"
  | "SETTLEMENT_CARRY"
  | "MONTHLY_PAYOUT";
export type ReconciliationStatus = "MATCHED" | "MISMATCHED" | "ERROR";
export type ReconciliationResultResponse = {
  id: number;
  type: ReconciliationType;
  targetDate: string;
  status: ReconciliationStatus;
  checkedCount: number;
  mismatchCount: number;
  mismatchRefs: string | null;
  errorMessage: string | null;
  executedAt: string;
};
export type PageResponse<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

// 가맹점·결제금액은 서버가 QR 에서, 지갑은 토큰에서 읽는다. cashbackUsed 를 생략하면 0 (결제금액 전액을 데브머니로 낸다)
export type PaymentRequest = { qrToken: string; cashbackUsed?: number };

// 내 지갑. availableCashback 은 결제에서 쓸 수 있는 캐시백(캐시백 잔액이 음수면 0)
export type WalletResponse = {
  devMoney: number;
  cashbackBalance: number;
  availableCashback: number;
  holdingLimit: number;
  monthlyChargeLimit: number;
  chargedThisMonth: number;
  chargeableAmount: number;
};
export type PaymentStatus = "PROCESSING" | "COMPLETED" | "FAILED" | "CANCELED";
export type PaymentResponse = {
  paymentId: number;
  status: PaymentStatus;
  amount: number;
  walletUsedAmount: number;
  cashbackUsedAmount: number;
  cashbackEarnedAmount: number | null;
  appliedRate: number | null;
  cashbackPayableAt: string | null;
  cashbackExclusionReason: string | null;
  failureCode?: string | null;
  requestedAt?: string | null; // 서버가 요청을 접수한 시각
  completedAt?: string | null; // 결제 완료 시각 (취소해도 그대로)
  canceledAt?: string | null; // 결제 취소 시각 (취소 전 null)
};

export type QrStatus = "WAITING" | "USED" | "EXPIRED";
export type QrCreateResponse = { token: string; amount: number; expiresAt: string };
export type QrScanResponse = { merchantId: number; merchantName: string; amount: number; cashbackEligible: boolean; expiresAt: string };
export type QrStatusResponse = { status: QrStatus; expiresAt: string };

// 점주의 결제 내역·매출. 결제는 완료한 날, 취소는 취소한 날에 센다 (일 정산과 같은 기준). 결제한 이용자 정보는 없다
export type MerchantPaymentItem = {
  paymentId: number;
  status: PaymentStatus;
  amount: number;
  walletUsedAmount: number;
  cashbackUsedAmount: number;
  completedAt: string | null;
  canceledAt: string | null;
};
export type MerchantPaymentsResponse = {
  from: string;
  to: string;
  paymentCount: number;
  paymentAmount: number;
  cancelCount: number;
  cancelAmount: number;
  netSales: number; // 결제 - 취소. 취소가 더 많은 날은 음수
  payments: PageResponse<MerchantPaymentItem>;
};
