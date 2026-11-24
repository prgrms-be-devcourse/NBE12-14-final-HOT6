import { api } from "@/lib/api";

// 회원가입·내 정보·PIN·탈퇴. 모두 로그인한 회원(토큰) 기준이다 (가입·로그인 제외)
export type SignupRequest = { name: string; email: string; password: string; phone: string; pin: string };
export type SignupResponse = { id: number; name: string; email: string; role: "USER" | "OWNER" | "ADMIN" };
export type MyInfo = { name: string; email: string; role: "USER" | "OWNER" | "ADMIN"; pinLocked: boolean; needsRelogin: boolean };

// 탈퇴: 미리보기로 환불 예정액을 확인하고, 그 금액과 멱등키를 담아 실행한다 (그 사이 금액이 달라지면 409 AUTH-008)
export type WithdrawalPreview = { refundAmount: number; pendingCashbackCount: number; pendingCashbackAmount: number; cashbackBalance: number };
export type WithdrawalResult = { refundedAmount: number; canceledEarningCount: number; canceledEarningAmount: number; withdrawnAt: string };

export const signup = (body: SignupRequest) => api<SignupResponse>("/users/signup", { method: "POST", body });
export const getMyInfo = () => api<MyInfo>("/users/me");

// 맞으면 200. 틀리면 400(AUTH-005, 메시지에 남은 횟수), 5번째로 틀리면 409(AUTH-006, 30분 뒤 자동 해제)
export const verifyPin = (pin: string) => api<null>("/users/pin/verify", { method: "POST", body: { pin } });

export const previewWithdrawal = () => api<WithdrawalPreview>("/users/withdraw", { method: "POST" });
export const settleWithdrawal = (expectedRefundAmount: number, idempotencyKey: string) =>
  api<WithdrawalResult>("/users/withdraw/settle", { method: "POST", body: { expectedRefundAmount }, idempotencyKey });
