// 백엔드 DTO 와 1:1 (src/main/java/.../dto)
export type MerchantRegisterRequest = {
  ownerId: number;
  name: string;
  address: string;
  cashbackEligible?: boolean;
};
export type MerchantRegisterResponse = { merchantId: number; name: string; address: string; cashbackEligible: boolean };

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
