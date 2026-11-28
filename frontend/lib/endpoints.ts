import { api } from "@/lib/api";
import type {
  ClosurePreviewResponse, ClosureResponse, ClosureSettlementRunResult,
  DailySettlementResponse, MerchantAccountResponse, MonthlySettlementResponse, MonthlySettlementRunResult,
  ReconciliationResultResponse, SettlementRunResult,
  MerchantPaymentItem, MerchantPaymentsResponse, MerchantRegisterRequest, MerchantRegisterResponse, MerchantResponse, MerchantUpdateRequest, PageResponse, PaymentRequest, PaymentResponse, UserPaymentsResponse,
  QrCreateResponse, QrLimit, QrScanResponse, QrStatusResponse, WalletResponse,
} from "@/lib/types";

export const registerMerchant = (body: MerchantRegisterRequest) =>
  api<MerchantRegisterResponse>("/merchants", { method: "POST", body, stay401: true }); // 401 이면 등록 화면이 "로그인이 필요해요"를 보여 준다

// 가맹점은 토큰의 회원으로 찾는다 (점주 기능은 /merchants/me 아래)
export const getMyMerchant = () => api<MerchantResponse>("/merchants/me");

export const updateMerchant = (body: MerchantUpdateRequest) => api<MerchantResponse>("/merchants/me", { method: "PATCH", body });

// 증빙 서류를 제출해 적립 제외 가맹점을 적립 대상으로 바꾼다(이미 적립 대상이면 같은 결과)
export const submitRevenueProof = (revenueProof: string) =>
  api<MerchantResponse>("/merchants/me/revenue-proof", { method: "PUT", body: { revenueProof } });

export const searchMerchants = (name: string, page = 0, size = 20) =>
  api<PageResponse<MerchantResponse>>(`/merchants?name=${encodeURIComponent(name)}&page=${page}&size=${size}`);

// 간편비밀번호(pin)는 결제를 시작하기 전에 서버가 다시 검증한다. 같은 멱등키로 다시 보낼 때도 pin 을 같이 보낸다
export const pay = (body: PaymentRequest & { pin: string }, idempotencyKey: string) =>
  api<PaymentResponse>("/payments", { method: "POST", body, idempotencyKey });

// 응답을 못 받은 결제를 같은 멱등키로 다시 조회한다 (새로 처리하지 않고 저장된 결과만 반환, 없으면 404 PAY-003)
export const getPaymentByKey = (key: string) => api<PaymentResponse>(`/payments/by-key/${encodeURIComponent(key)}`);

// 로그인한 회원의 지갑(데브머니, 결제 가능 캐시백, 한도). 지갑은 토큰으로 찾는다
export const getMyWallet = () => api<WalletResponse>("/wallet");

// 가맹점주 전액 취소. 가맹점은 토큰의 회원으로 찾는다
export const cancelPayment = (paymentId: number) => api<PaymentResponse>(`/payments/${paymentId}/cancel`, { method: "POST" });

// 내 결제 내역. 완료·취소만 최신순이고, status 를 생략하면 둘 다 준다. 지갑은 토큰의 회원으로 찾는다
export const getMyPayments = (status: "COMPLETED" | "CANCELED" | undefined, page = 0, size = 10) =>
  api<UserPaymentsResponse>(`/payments?page=${page}&size=${size}${status ? `&status=${status}` : ""}`);

// 점주의 결제 내역과 매출. from·to 는 yyyy-MM-dd(양 끝 포함, 최대 31일)이고 가맹점은 토큰의 회원으로 찾는다
export const getMerchantPayments = (from: string, to: string, page = 0, size = 10) =>
  api<MerchantPaymentsResponse>(`/merchants/me/payments?from=${from}&to=${to}&page=${page}&size=${size}`);

// 결제번호로 내 가맹점의 결제 한 건(영수증)을 조회한다. 없거나 다른 가맹점의 결제면 PAY-003
export const getMerchantPayment = (paymentId: number) => api<MerchantPaymentItem>(`/merchants/me/payments/${paymentId}`);

export const createDynamicQr = (amount: number) =>
  api<QrCreateResponse>("/merchants/me/qr/dynamic", { method: "POST", body: { amount } });

// 정산 조회 (점주). 일 정산은 최대 31일, 월 정산은 최대 12개월
export const getDailySettlements = (from: string, to: string) =>
  api<DailySettlementResponse[]>(`/merchants/me/settlements/daily?from=${from}&to=${to}`);

export const getMonthlySettlements = (from: string, to: string) =>
  api<MonthlySettlementResponse[]>(`/merchants/me/settlements/monthly?from=${from}&to=${to}`);

export const getMyAccount = () => api<MerchantAccountResponse>("/merchants/me/account");

// 가맹점 해지 (점주). 신청하면 바로 QR 생성과 결제가 막히고 철회할 수 없다
export const getClosurePreview = () => api<ClosurePreviewResponse>("/merchants/me/closure/preview");

export const requestClosure = () => api<ClosureResponse>("/merchants/me/closure", { method: "POST" });

// 해지 정산 수동 실행 (운영자). 해지 진행 중인 가맹점 전체를 정산하고 해지를 끝낸다
export const runClosureSettlement = () =>
  api<ClosureSettlementRunResult>("/admin/merchants/closures/run", { method: "POST" });

// 정산·대사 수동 실행 (운영자). 날짜·월을 생략하면 서버가 어제·지난달로 실행한다
const withParam = (path: string, key: string, value?: string) => (value ? `${path}?${key}=${value}` : path);

export const runDailySettlement = (date?: string) =>
  api<SettlementRunResult>(withParam("/admin/settlements/daily/run", "date", date), { method: "POST" });

export const runMonthlySettlement = (month?: string) =>
  api<MonthlySettlementRunResult>(withParam("/admin/settlements/monthly/run", "month", month), { method: "POST" });

export const runReconciliation = (date?: string) =>
  api<ReconciliationResultResponse[]>(withParam("/admin/settlements/reconciliation/run", "date", date), { method: "POST" });

export const getReconciliations = (from: string, to: string) =>
  api<ReconciliationResultResponse[]>(`/admin/settlements/reconciliation?from=${from}&to=${to}`);

export const getQrLimit = () => api<QrLimit>("/qr/limit");
export const scanQr = (token: string) => api<QrScanResponse>(`/qr/${encodeURIComponent(token)}`);

export const getQrStatus = (token: string) => api<QrStatusResponse>(`/qr/${encodeURIComponent(token)}/status`);

export const secondsUntil = (iso: string) => Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
