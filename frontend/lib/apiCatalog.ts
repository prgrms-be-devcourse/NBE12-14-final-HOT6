export type Endpoint = {
  id: string;
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  path: string;
  summary: string;
  tag: string;
  body?: string;
  idempotency?: boolean;
};

// 백엔드에 이미 있는 API. 새 API 는 OpenAPI 문서가 열려 있으면 자동으로 추가되고,
// 막혀 있으면 이 목록에 한 줄 추가하면 된다.
export const BASE_CATALOG: Endpoint[] = [
  {
    id: "POST /api/merchants", method: "POST", path: "/api/merchants", tag: "가맹점", summary: "가맹점 등록",
    body: JSON.stringify({ ownerId: 1, name: "테스트카페", address: "서울 ○○구 ○○로 12", cashbackEligible: true }, null, 2),
  },
  {
    id: "POST /api/payments", method: "POST", path: "/api/payments", tag: "결제", summary: "변동 QR 결제", idempotency: true,
    body: JSON.stringify({ walletId: 1, merchantId: 1, qrId: 1, amount: 12000 }, null, 2),
  },
];
