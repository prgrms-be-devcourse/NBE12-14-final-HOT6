import { api } from "@/lib/api";
import type {
  MerchantRegisterRequest, MerchantRegisterResponse, MerchantResponse, MerchantUpdateRequest, PageResponse, PaymentRequest, PaymentResponse,
  QrCreateResponse, QrScanResponse, QrStatusResponse,
} from "@/lib/types";

export const registerMerchant = (body: MerchantRegisterRequest) =>
  api<MerchantRegisterResponse>("/merchants", { method: "POST", body });

// 점주 본인 가맹점은 토큰의 회원으로 찾는다 (/me)
export const updateMerchant = (body: MerchantUpdateRequest) =>
  api<MerchantResponse>("/merchants/me", { method: "PATCH", body });

export const searchMerchants = (name: string, page = 0, size = 20) =>
  api<PageResponse<MerchantResponse>>(`/merchants?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);

export const pay = (body: PaymentRequest, idempotencyKey: string) =>
  api<PaymentResponse>("/payments", { method: "POST", body, idempotencyKey });

// 응답을 못 받은 결제를 같은 멱등키로 다시 조회한다 (새로 처리하지 않고 저장된 결과만 반환, 없으면 404 PAY-003)
export const getPaymentByKey = (walletId: number, key: string) =>
  api<PaymentResponse>(`/payments/by-key/${encodeURIComponent(key)}?walletId=${walletId}`);

// 가맹점주 전액 취소. 가맹점은 토큰의 회원으로 정한다
export const cancelPayment = (paymentId: number) =>
  api<PaymentResponse>(`/payments/${paymentId}/cancel`, { method: "POST" });

export const createDynamicQr = (amount: number) =>
  api<QrCreateResponse>("/merchants/me/qr/dynamic", { method: "POST", body: { amount } });

export const scanQr = (token: string) => api<QrScanResponse>(`/qr/${encodeURIComponent(token)}`);

export const getQrStatus = (token: string) => api<QrStatusResponse>(`/qr/${encodeURIComponent(token)}/status`);

export const secondsUntil = (iso: string) => Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
