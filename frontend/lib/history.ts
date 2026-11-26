import { type Earning, type EarningStatus, getEarnings } from "@/lib/cashbackApi";
import { getMyPayments } from "@/lib/endpoints";
import type { PageResponse, UserPaymentItem } from "@/lib/types";
import { getWalletTransactions } from "@/lib/walletApi";

// 이용내역·캐시백 내역은 서로 다른 API 의 목록을 시간순으로 섞어 보여 준다.
// 합친 목록을 주는 API 가 없어 전부 받아 온 뒤 화면에서 정렬하고 쪽을 나눈다.

export const PAGE_SIZE = 20; // 내역 한 쪽에 보여 주는 건수
const FETCH_SIZE = 100; // 서버가 허용하는 한 번의 최대 건수

export const fmt = (iso?: string | null) => (iso ? `${iso.slice(5, 7)}.${iso.slice(8, 10)} ${iso.slice(11, 16)}` : "-");
export const fmtFull = (iso?: string | null) => (iso ? iso.slice(0, 16).replace("T", " ") : "-");

// 모든 쪽을 차례로 받는다. 받는 사이에 새 항목이 생기면 쪽 경계가 밀려 겹치므로 id 로 걸러 낸다
async function fetchAll<T>(load: (page: number) => Promise<PageResponse<T>>, idOf: (item: T) => number): Promise<T[]> {
  const items = new Map<number, T>();
  let page = 0;
  let totalPages = 1;
  do {
    const res = await load(page);
    res.content.forEach((item) => items.set(idOf(item), item));
    totalPages = res.totalPages;
    page += 1;
  } while (page < totalPages);
  return [...items.values()];
}

const getAllPayments = () =>
  fetchAll((page) => getMyPayments(undefined, page, FETCH_SIZE).then((r) => r.payments), (p) => p.paymentId);

const getAllEarnings = () => fetchAll((page) => getEarnings(undefined, page, FETCH_SIZE), (e) => e.earningId);

// 결제별 적립 상태 (결제 하나에 적립 건은 하나). 영수증이 지급 전과 지급 후를 구분할 때 쓴다
export const getEarningStatuses = async () => new Map((await getAllEarnings()).map((e) => [e.paymentId, e.status]));

const newestFirst = (a: { at: string }, b: { at: string }) => (a.at < b.at ? 1 : a.at > b.at ? -1 : 0);

// 이용내역: 결제(완료·취소)와 데브머니 충전·환불
export type UsageItem =
  | { kind: "payment"; key: string; at: string; payment: UserPaymentItem; earningStatus?: EarningStatus }
  | { kind: "charge" | "refund"; key: string; at: string; amount: number; balanceAfter: number };

export async function getUsageHistory(): Promise<UsageItem[]> {
  const [payments, transactions, statuses] = await Promise.all([
    getAllPayments(),
    fetchAll((page) => getWalletTransactions(page, FETCH_SIZE), (t) => t.transactionId),
    getEarningStatuses(),
  ]);
  const paid: UsageItem[] = payments.map((payment) => ({
    kind: "payment", key: `payment-${payment.paymentId}`, at: payment.completedAt ?? "", payment,
    earningStatus: statuses.get(payment.paymentId),
  }));
  const moves: UsageItem[] = transactions
    .filter((t) => t.type === "CHARGE" || t.type === "REFUND")
    .map((t) => ({
      kind: t.type === "CHARGE" ? "charge" : "refund", key: `wallet-${t.transactionId}`, at: t.createdAt,
      amount: Math.abs(t.amount), balanceAfter: t.balanceAfter,
    }));
  return [...paid, ...moves].sort(newestFirst);
}

// 캐시백 내역: 적립(예정·지급 완료·취소·회수·제외)과 결제에 쓴 캐시백. 가맹점 이름과 영수증 내용은 결제 내역에서 가져온다
// payment 는 그 적립·사용이 생긴 결제. 적립 건의 결제가 결제 내역에 없으면 비어 있다
// payableAt 은 그 결제의 적립이 아직 지급 전(예정)일 때의 지급 예정일, paidAt 은 지급 완료일 때의 지급일
export type CashbackItem = { key: string; at: string; title: string; payableAt?: string | null; paidAt?: string | null; earningStatus?: EarningStatus } & (
  | { kind: "earning"; payment?: UserPaymentItem; earning: Earning }
  | { kind: "use"; payment: UserPaymentItem; amount: number; canceled: boolean }
);

const merchantTitle = (p: UserPaymentItem) => p.merchantName ?? "알 수 없는 가맹점";

export async function getCashbackHistory(): Promise<CashbackItem[]> {
  const [earnings, payments] = await Promise.all([getAllEarnings(), getAllPayments()]);
  const byId = new Map(payments.map((p) => [p.paymentId, p]));
  const statusById = new Map(earnings.map((e) => [e.paymentId, e.status]));
  const payableById = new Map(earnings.filter((e) => e.status === "PENDING").map((e) => [e.paymentId, e.payableAt]));
  const paidById = new Map(earnings.filter((e) => e.status === "PAID").map((e) => [e.paymentId, e.paidAt]));
  const earned: CashbackItem[] = earnings.map((earning) => {
    const payment = byId.get(earning.paymentId);
    return {
      kind: "earning", key: `earning-${earning.earningId}`, at: earning.requestedAt,
      title: payment ? merchantTitle(payment) : `결제 #${earning.paymentId}`, payment, earning,
      payableAt: payableById.get(earning.paymentId), paidAt: paidById.get(earning.paymentId), earningStatus: earning.status,
    };
  });
  const used: CashbackItem[] = payments
    .filter((p) => p.cashbackUsedAmount > 0)
    .map((p) => ({
      kind: "use", key: `use-${p.paymentId}`, at: p.completedAt ?? "", title: merchantTitle(p), payment: p,
      amount: p.cashbackUsedAmount, canceled: p.status === "CANCELED",
      payableAt: payableById.get(p.paymentId), paidAt: paidById.get(p.paymentId), earningStatus: statusById.get(p.paymentId),
    }));
  return [...earned, ...used].sort(newestFirst);
}
