// 백엔드 DTO 와 1:1 (src/main/java/.../dto)
// 적립 대상 여부는 서버가 결정한다: revenueProof(연매출 30억 미만 증빙 서류)가 있으면 자동 승인
export type MerchantRegisterRequest = {
  ownerId: number;
  name: string;
  address: string;
  revenueProof?: string;
};
export type MerchantRegisterResponse = { merchantId: number; name: string; address: string; cashbackEligible: boolean };
export type MerchantResponse = MerchantRegisterResponse;
export type MerchantUpdateRequest = { name?: string; address?: string };
export type PageResponse<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export type PaymentRequest = { walletId: number; merchantId: number; qrId: number; amount: number };
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
};
