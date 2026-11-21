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
    body: JSON.stringify({ ownerId: 1, name: "테스트카페", address: "서울 ○○구 ○○로 12", revenueProof: "revenue-proof.pdf" }, null, 2),
  },
  {
    id: "PATCH /api/merchants/{merchantId}", method: "PATCH", path: "/api/merchants/1", tag: "가맹점", summary: "가맹점 수정 (이름·주소)",
    body: JSON.stringify({ name: "수정된카페", address: "서울 ○○구 ○○로 99" }, null, 2),
  },
  {
    id: "GET /api/merchants", method: "GET", path: "/api/merchants?name=카페&page=0&size=20", tag: "가맹점", summary: "가맹점 이름 검색",
  },
  {
    id: "POST /api/payments", method: "POST", path: "/api/payments", tag: "결제", summary: "변동 QR 결제", idempotency: true,
    body: JSON.stringify({ walletId: 1, merchantId: 1, qrToken: "QR 생성 응답의 token", amount: 12000 }, null, 2),
  },
  {
    id: "POST /api/merchants/{merchantId}/qr/dynamic", method: "POST", path: "/api/merchants/1/qr/dynamic", tag: "QR", summary: "결제 QR 생성 (3분 만료)",
    body: JSON.stringify({ amount: 12000 }, null, 2),
  },
  { id: "GET /api/qr/{token}", method: "GET", path: "/api/qr/{token}", tag: "QR", summary: "QR 스캔 조회" },
  { id: "GET /api/qr/{token}/status", method: "GET", path: "/api/qr/{token}/status", tag: "QR", summary: "QR 상태 조회" },
];
