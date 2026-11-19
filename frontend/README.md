# DevPay 프론트엔드

캐시백 QR 결제 서비스의 웹 화면입니다. (Next.js App Router · TypeScript · Tailwind CSS)

## 실행

```bash
# 백엔드를 8080 이 아닌 포트로 띄웠다면 BACKEND_URL 로 알려 준다 (기본 http://localhost:8080)
BACKEND_URL=http://localhost:8080 npm run dev
```

브라우저는 `/api/...` 로 호출하고 Next 가 백엔드로 넘기므로 CORS 설정이 필요 없다.
`next start`(운영 빌드)는 프록시 주소가 빌드 시점에 고정되므로 개발 중에는 `npm run dev` 를 쓴다.

## 화면

| 경로 | 설명 |
|---|---|
| `/` | DevPay 홈 (이용자 · 가맹점주 · 운영자 로그인) |
| `/login` ... | 이용자 앱 (모바일웹) |
| `/merchant/login` ... | 가맹점주 앱 (모바일웹) |
| `/admin/login` ... | 운영자 웹 |
| `/dev` | 개발용 전체 화면 목록, 개발용 식별자 |
| `/dev/api` | API 테스트 (스웨거처럼 요청 전송) |

## 백엔드 연동

- 연동된 API: `POST /api/merchants`(가맹점 등록), `PATCH /api/merchants/{id}`(이름·주소 수정), `POST /api/payments`(QR 결제)
- `GET /api/merchants?name=`(이름 검색)은 `lib/endpoints.ts` 에 함수만 있고 아직 화면에는 연결하지 않았다. (응답에 위치·업종이 없어 지도에 못 찍음)
- 가맹점의 적립 대상 여부는 서버가 결정한다: 등록 시 증빙 서류(`revenueProof`)를 첨부하면 적립 대상
- 나머지는 `lib/mock.ts` 의 목 데이터. API 가 생기면 `lib/endpoints.ts` 에 함수를 추가하고 화면에서 호출을 바꾼다.
- 요청·응답 타입은 `lib/types.ts` (백엔드 DTO 와 1:1)
- 서비스 한도는 `lib/limits.ts` 한 곳에서 관리한다.
