import { api } from "@/lib/api";
import type { MerchantRegisterRequest, MerchantRegisterResponse, PaymentRequest, PaymentResponse } from "@/lib/types";

export const registerMerchant = (body: MerchantRegisterRequest) =>
  api<MerchantRegisterResponse>("/merchants", { method: "POST", body });

export const pay = (body: PaymentRequest, idempotencyKey: string) =>
  api<PaymentResponse>("/payments", { method: "POST", body, idempotencyKey });
