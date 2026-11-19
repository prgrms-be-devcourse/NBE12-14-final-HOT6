import { api } from "@/lib/api";
import type {
  MerchantRegisterRequest, MerchantRegisterResponse, MerchantResponse, MerchantUpdateRequest, PageResponse, PaymentRequest, PaymentResponse,
} from "@/lib/types";

export const registerMerchant = (body: MerchantRegisterRequest) =>
  api<MerchantRegisterResponse>("/merchants", { method: "POST", body });

export const updateMerchant = (merchantId: number, body: MerchantUpdateRequest) =>
  api<MerchantResponse>(`/merchants/${merchantId}`, { method: "PATCH", body });

export const searchMerchants = (name: string, page = 0, size = 20) =>
  api<PageResponse<MerchantResponse>>(`/merchants?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);

export const pay = (body: PaymentRequest, idempotencyKey: string) =>
  api<PaymentResponse>("/payments", { method: "POST", body, idempotencyKey });
