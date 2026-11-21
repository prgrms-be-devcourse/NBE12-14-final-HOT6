import { api } from "@/lib/api";
import type {
  MerchantRegisterRequest, MerchantRegisterResponse, MerchantResponse, MerchantUpdateRequest, PageResponse, PaymentRequest, PaymentResponse,
  QrCreateResponse, QrScanResponse, QrStatusResponse,
} from "@/lib/types";

export const registerMerchant = (body: MerchantRegisterRequest) =>
  api<MerchantRegisterResponse>("/merchants", { method: "POST", body });

export const updateMerchant = (merchantId: number, body: MerchantUpdateRequest) =>
  api<MerchantResponse>(`/merchants/${merchantId}`, { method: "PATCH", body });

export const searchMerchants = (name: string, page = 0, size = 20) =>
  api<PageResponse<MerchantResponse>>(`/merchants?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);

export const pay = (body: PaymentRequest, idempotencyKey: string) =>
  api<PaymentResponse>("/payments", { method: "POST", body, idempotencyKey });

export const createDynamicQr = (merchantId: number, amount: number) =>
  api<QrCreateResponse>(`/merchants/${merchantId}/qr/dynamic`, { method: "POST", body: { amount } });

export const scanQr = (token: string) => api<QrScanResponse>(`/qr/${encodeURIComponent(token)}`);

export const getQrStatus = (token: string) => api<QrStatusResponse>(`/qr/${encodeURIComponent(token)}/status`);

export const secondsUntil = (iso: string) => Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
