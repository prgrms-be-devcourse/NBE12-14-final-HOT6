import { api } from "@/lib/api";
import type {
  MerchantRegisterRequest, MerchantRegisterResponse, MerchantResponse, MerchantUpdateRequest, PageResponse, PaymentRequest, PaymentResponse,
  QrCreateResponse, QrScanResponse, QrStatusResponse, WalletResponse,
} from "@/lib/types";

export const registerMerchant = (body: MerchantRegisterRequest) =>
  api<MerchantRegisterResponse>("/merchants", { method: "POST", body });

export const updateMerchant = (merchantId: number, body: MerchantUpdateRequest) =>
  api<MerchantResponse>(`/merchants/${merchantId}`, { method: "PATCH", body });

export const searchMerchants = (name: string, page = 0, size = 20) =>
  api<PageResponse<MerchantResponse>>(`/merchants?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);

export const pay = (body: PaymentRequest, idempotencyKey: string) =>
  api<PaymentResponse>("/payments", { method: "POST", body, idempotencyKey });

// 응답을 못 받은 결제를 같은 멱등키로 다시 조회한다 (새로 처리하지 않고 저장된 결과만 반환, 없으면 404 PAY-003)
export const getPaymentByKey = (key: string) => api<PaymentResponse>(`/payments/by-key/${encodeURIComponent(key)}`);

// 로그인한 회원의 지갑(데브머니, 결제 가능 캐시백, 한도). 지갑은 토큰으로 찾는다
export const getMyWallet = () => api<WalletResponse>("/wallet");

// 가맹점주 전액 취소. merchantId 는 인증 연동 전까지 임시로 쿼리 파라미터로 보낸다
export const cancelPayment = (paymentId: number, merchantId: number) =>
  api<PaymentResponse>(`/payments/${paymentId}/cancel?merchantId=${merchantId}`, { method: "POST" });

export const createDynamicQr = (merchantId: number, amount: number) =>
  api<QrCreateResponse>(`/merchants/${merchantId}/qr/dynamic`, { method: "POST", body: { amount } });

export const scanQr = (token: string) => api<QrScanResponse>(`/qr/${encodeURIComponent(token)}`);

export const getQrStatus = (token: string) => api<QrStatusResponse>(`/qr/${encodeURIComponent(token)}/status`);

export const secondsUntil = (iso: string) => Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
