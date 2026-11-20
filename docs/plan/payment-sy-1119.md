# 결제(Payment) 기획서 — A 신영

> 담당: 결제·동시성 / 개발 11/16 ~ 12/4, 중간보고 11/24, 발표 12/7
> **구현 마감: 11/23(월)까지 사장님 결제 내역을 제외한 전체** (멱등키, 결제, 결제 결과 재조회, 결제 취소)
> 마지막 갱신: 2026-11-19 (T3 실패 처리 구현, 정책 #5 확정, QR 식별을 `qrToken`으로 변경)

## 1. 한눈에 보기

결제 코어를 맡습니다. 목표는 **돈이 틀리지 않게** 하는 것입니다.

| 기능 | API | 한 줄 요약 |
| --- | --- | --- |
| 멱등키 | 공통(검증) + 결제(중복 판정) | 같은 요청은 한 번만 처리하고, 재전송 시 기존 결과를 반환 |
| 공통 에러 코드 | 공통 | 실패 사유를 `{DOMAIN}-{NNN}` 코드로 통일 |
| 결제 | `POST /api/payments` | QR/가맹점 검증, 지갑 차감, 캐시백 예산 예약, QR 사용 처리를 한 흐름으로 |
| 결제 결과 재조회 | `GET /api/payments/by-key/{key}` | 응답을 못 받았을 때 멱등키로 결과 확인 |
| 결제 취소 | `POST /api/payments/{id}/cancel` | 지갑 환급, 캐시백 적립 취소, 중복 취소 방지 |
| 사장님 결제 내역 | `GET /api/merchants/{id}/payments` | 결제·취소·순매출 조회 |

추가 업무: 결제 트랜잭션에 서킷브레이커·타임아웃·재시도·벌크헤드 적용 (리질리언스 패턴, C와 공동). **적용 경계는 합의 필요** (지갑 호출 지점만 vs 트랜잭션 전체).

## 2. 확정된 설계

### 2-1. 금액 타입
- DB `BIGINT` ↔ 자바 `Long`. 캐시백 소수점은 **버림으로 확정** (0.3원 누적 정책 폐지).
- 적립액 = `floor(데브머니 사용분 × 요율)`, 요율만 `NUMERIC(5,4)` ↔ `BigDecimal`.
- 취소 시 회수액은 재계산하지 않고 **저장된 적립액**을 사용합니다.

### 2-2. 멱등키
- 프론트가 UUID를 결제 시도마다 생성해 헤더 `Idempotency-Key`로 전송합니다. 응답을 못 받아 **자동 재전송**할 때만 같은 키를 쓰고, 실패한 뒤 사용자가 **다시 결제**할 때는 새 키를 만듭니다 (같은 키는 아래 표처럼 기존 결과를 돌려주기 때문).
- 서버는 헤더 **형식만** 검증합니다 (`IdempotencyKeys.validate`).
  - 없음/빈 값/공백 → `COM-002`, UUID 형식 아님 → `COM-001`
- 중복 판단은 각 도메인 테이블의 `(wallet_id, idempotency_key)` UNIQUE 제약이 합니다.

결제에서 같은 키로 다시 요청이 오면 다음과 같이 처리합니다.

| 같은 키로 재요청 | 결과 |
| --- | --- |
| 요청 내용(가맹점·QR·금액)이 다름 | `COM-003` (409) |
| 내용 같음 + 기존 건이 처리 중 | `COM-004` (409) |
| 내용 같음 + 기존 건이 완료/취소/실패 | 에러 없이 **기존 결과 그대로 반환** (재실행 없음). 실패 건은 `status=FAILED`와 `failureCode`가 담겨 옴 |

- **실패 건 재시도 정책(합의 #5)은 "재시도 없음"으로 확정**했습니다. 같은 키로는 다시 처리하지 않고, 다시 결제하려면 새 멱등키로 요청합니다 (QR은 T2 롤백으로 `WAITING`에 돌아와 있으면 그대로 쓸 수 있음). 이에 따라 `reclaimFailed`(FAILED→PROCESSING)는 삭제했습니다.

### 2-3. 트랜잭션 구조
```
Controller → PaymentFacade (트랜잭션 없음)
  ├ PaymentIdempotencyService  T1: payment INSERT(PROCESSING) 후 즉시 커밋 (선점)
  ├ PaymentTxService T2: 결제 행 락 → 지갑 차감 → QR 사용 → 적립 예약 → COMPLETED
  │                      (이어서 가맹점 매출 기록(D)을 같은 트랜잭션에 추가 예정, #55)
  └ PaymentFailureService T3: T2 롤백 후 새 트랜잭션에서 FAILED + failure_code 기록
```
- Facade에 트랜잭션을 두지 않는 이유: T1이 먼저 커밋되어 "이 키로 시도했다"는 기록이 남아야 하고, 유니크 위반 후에는 같은 트랜잭션을 쓸 수 없기 때문입니다.
- 서비스는 서로 다른 클래스로 분리합니다 (같은 클래스 내부 호출은 `@Transactional`이 적용되지 않음).
- `BusinessException`은 `RuntimeException`이라 롤백됩니다.

**Facade의 try/catch (T2 실패 → T3)**

| 위치 | 잡는 예외 | 처리 |
| --- | --- | --- |
| T1 `claim` | `DataIntegrityViolationException` (같은 지갑·키의 행이 이미 있음) | 기존 행을 조회해 완료/취소/실패면 결과 반환, 처리 중이면 `COM-004`. T2로 가지 않음 |
| T2 `tx` | `BusinessException` | `COM-004`가 **아니면** 던진 에러 코드를 `failure_code`로 기록(T3) 후 예외를 다시 던짐 |
| T2 `tx` | 그 외 `RuntimeException` | `COM-010`을 기록(T3) 후 다시 던짐 |

- T2 호출은 T1 `try` 밖에 둡니다. 본처리 중 나온 `DataIntegrityViolationException`이 "이미 있는 행"으로 오해되어 T1의 `catch`로 빠지지 않게 하기 위해서입니다.
- `BusinessException`도 `RuntimeException`의 하위 클래스라 `catch`는 `BusinessException`이 먼저입니다.
- `COM-004`("다른 요청이 처리 중")는 이 건의 실패가 아니므로 기록하지 않습니다.
- T3 자체가 실패하면 행은 `PROCESSING`으로 남습니다 (정리 정책은 합의 #6).
- T3는 `PROCESSING`인 건만 `FAILED`로 바꿉니다. 이미 완료/실패한 건에 다시 호출해도 상태와 첫 실패 코드가 바뀌지 않습니다.

### 2-4. 에러 코드
- 형식 `{DOMAIN}-{NNN}`, 인터페이스 `ErrorCode` + 도메인별 enum.
- 공통(COM) 현재 등록: 

| 코드 | HTTP | 의미 |
| --- | --- | --- |
| COM-001 | 400 | 잘못된 요청 |
| COM-002 | 400 | Idempotency-Key 헤더 없음 |
| COM-003 | 409 | 같은 키로 다른 내용 요청 |
| COM-004 | 409 | 이전 요청 처리 중 |
| COM-005~008 | 401/403 | 인증·권한 (Security 연동 후 사용) |
| COM-009 | 404 | 리소스 없음 |
| COM-010 | 500 | 서버 내부 오류 |
| COM-011 | 503 | 외부 서비스 연동 실패 |
| COM-012 | 405 | 지원하지 않는 HTTP 메서드 |

- 결제(PAY) 코드 (`PaymentErrorCode`): 결제 도메인이 직접 판단하는 실패만 정의합니다.

| 코드 | HTTP | 의미 | 비고 |
| --- | --- | --- | --- |
| PAY-001 | 400 | 결제 한도 초과 | **현재 쓰지 않음.** 결제 1회 한도를 QR 금액 상한(D)으로 갈음하기로 하면 삭제 (합의 #14) |
| PAY-002 | 409 | 잔액 부족 | **임시.** 지갑(C)의 에러 코드가 생기면 삭제하고 그 코드를 씀 |

- 다른 도메인이 던지는 실패는 그 도메인의 코드를 그대로 씁니다 (QR-001~004, 캐시백 CSH-xxx, 가맹점 MER-001 등). T3는 던져진 `BusinessException`의 코드를 `payment.failure_code`에 **그대로 기록**합니다.
- `QR_REQUEST_MISMATCH`(요청 가맹점·금액이 QR과 다름)는 결제 쪽에 두지 않고 D의 `QR-004`로 받습니다 (D의 #54).

### 2-5. 시간 기준
- 결제가 만드는 시각(`requestedAt`, `completedAt`)은 모두 `Clock`(KST) 기준 `LocalDateTime`입니다. `LocalDateTime.now()`를 직접 부르지 않습니다.
- 엔티티는 시각을 직접 만들지 않고 서비스가 `Clock`으로 만든 값을 인자로 받습니다 (`Payment.complete(..., completedAt)`).
- 적립 요율과 월 한도의 기준은 **최초 접수 시각(`requestedAt`)** 입니다.
- 통합 테스트는 `@IntegrationTest`(테스트 DB + `MutableClock`)를 붙여 시간을 고정합니다 (PR #19).

### 2-6. 결제 응답(영수증 기본 필드)
결제금액, 데브머니 사용분, 캐시백 사용분, 적립 캐시백, 적용 요율, **지급 예정 시각, 적립 제외 사유, 결제 실패 코드(`failureCode`)** 를 응답에 담습니다.
- 지급 예정 시각과 제외 사유는 `CashbackService.reserve`의 반환값(`CashbackReserveResponse`)이며, 재조회해도 같은 영수증이 나오도록 `payment`에 저장합니다 (`cashback_payable_at`, `cashback_exclusion_reason`).
- `failureCode`는 실패 건에만 값이 있습니다. 같은 멱등키로 다시 요청해 기존 실패 결과를 받을 때도 실패 사유를 알 수 있습니다.
- **월 한도 상태(남은 한도)는 아직 없습니다.** 머지된 `CashbackReserveResponse`에 월 한도 필드가 없으므로(제외 사유 `MONTHLY_LIMIT_EXCEEDED`만 있음) 합의 #13에서 정합니다.

### 2-7. 데이터 모델 (`payment` 테이블)

엔티티 `Payment` 기준입니다. DB는 `ddl-auto: update`로 생성합니다. 복합 유니크 `(wallet_id, idempotency_key)`가 중복 요청을 막습니다.

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| `id` | BIGINT PK | N | 결제 ID |
| `wallet_id` | BIGINT | N | 지갑 (JWT 연동 전에는 요청 본문, 합의 #7) |
| `merchant_id` | BIGINT | N | 가맹점 |
| `qr_token` | VARCHAR(64) | Y | 이용자가 스캔한 QR 토큰(UUID). T1에서 저장 **(변경: 기존 `qr_id`)** |
| `idempotency_key` | VARCHAR(100) | N | 멱등키 |
| `amount` | BIGINT | N | 총 결제액 = 데브머니 사용분 + 캐시백 사용분 |
| `wallet_used_amount` / `cashback_used_amount` | BIGINT | N | T1에서 0으로 시작, T2에서 확정 |
| `status` | VARCHAR(20) | N | PROCESSING / COMPLETED / FAILED / CANCELED |
| `failure_code` | VARCHAR(50) | Y | T3가 기록 (예: `QR-003`, `COM-010`) |
| `applied_policy_id`, `applied_rate`(NUMERIC(5,4)), `cashback_earned_amount` | | Y | 적립 정책·요율 스냅샷, 적립액 (T2) |
| `cashback_payable_at`, `cashback_exclusion_reason` | | Y | 지급 예정 시각, 적립 제외 사유 (T2) |
| `requested_at` | TIMESTAMP | N | 서버 최초 접수 시각 (요율·월 한도·QR 만료 판정 기준) |
| `completed_at`, `canceled_at`, `canceled_by` | | Y | 완료 시각, 취소 시각, 취소한 가맹점주 (취소 구현 전) |

**ERD 변경 (결제)**

| 구분 | 내용 | 이유 |
| --- | --- | --- |
| 변경 | `qr_id`(BIGINT) → `qr_token`(VARCHAR(64)) | QR 이미지에는 토큰(UUID)만 담기고 결제 요청이 토큰을 받기 때문. 같은 요청 비교와 QR 사용 처리에 토큰을 씀 |
| 미반영 | `payment.monthly_budget_id` (`project-spec` 8절에 "컬럼 추가"로 적혀 있음) | 합의 #10 방향대로 `cashback_earning` 쪽에 두고 `payment`에는 추가하지 않음. 확정되면 `project-spec` 8절도 맞춰야 함 |

- 기존 DB에는 `qr_id` 컬럼이 남아 있을 수 있습니다 (`ddl-auto: update`는 컬럼을 지우지 않음). nullable이라 동작에는 영향이 없고, 깨끗하게 쓰려면 로컬 DB를 다시 만들면 됩니다.
- `project-spec` 8절 "ERD 변경 사항" 표에는 위 `qr_id → qr_token` 행이 아직 없습니다 (팀 공통 문서라 별도로 추가 필요).

## 3. 진행 현황

| 항목 | 상태 |
| --- | --- |
| 공통 에러 코드·응답·예외 처리 (#4, PR #10) | 완료 (dev 머지) |
| 멱등키 헤더 검증 (#2) | 완료 (PR #13, dev 머지. 이전 PR이 `feat/#4`로 머지되어 dev에 없던 것을 바로잡음) |
| 결제 요청 접수(멱등 선점) API (#12) | 완료 (PR #15, dev 머지): 엔티티, 리포지토리, DTO, claim/기존 요청 판정, 컨트롤러, 통합 테스트. FAILED 재시도 테스트는 정책 #5 확정(재시도 없음)에 따라 "기존 실패 결과 반환" 기준으로 변경해 활성화 |
| 결제 본처리 T2 (#16) | 완료 (PR #39, dev 머지): `PaymentTxService`(락·차감·QR·적립 예약·완료), 가맹점 적립 대상 조회, 영수증 필드. 지갑·QR은 인터페이스 없이 `payment/mock`의 Mock 클래스로 연결 |
| 캐시백 연동 서비스 (B, PR #23) | 완료. `CashbackMockService`를 삭제하고 실제 `CashbackService`로 교체함 (계약 타입은 #33에서 `CashbackReserveRequest/Response`로 이름 정리) |
| 실패 처리 T3 (#16) | 구현 완료, PR #56 리뷰 대기 (`Closes #16`): `PaymentFailureService`, `Payment.failure`, Facade의 try/catch, `PaymentErrorCode`, `PaymentResponse.failureCode`, 통합 테스트 8건·단위 테스트 4건. **#39 리뷰 피드백 반영 포함**: QR 식별 `qrId` → `qrToken`, 지갑 차감 시그니처(`debit(walletId, amount, type, refKey, allowNegative)` → `DebitResult`), QR 사용 처리 시그니처(`markUsed(qrToken, merchantId, amount, requestedAt)`) |
| Mock → 실제 서비스 교체 | 이슈 #55 (MVP 개발 마일스톤). 선행: D의 #54(token 기준 `markUsed`), C의 `WalletService.debit` 머지 (C 쪽 이슈는 아직 없어 확인 필요). 가맹점 매출 기록(D) 호출도 이때 추가 |
| 재조회, 취소 | 미착수 |
| 사장님 결제 내역 | 11/23 마감 범위 제외 (11/25~26) |

## 4. 일정 (계획)

**11/23(월) 구현 마감 범위**: 멱등키 처리, 결제(멱등 선점 + 본처리 + 실패 기록), 결제 결과 재조회, 결제 취소, 같은 키·동시 요청 테스트.
**시간되면 추가**: 락 없는 결제로 동시 요청 오류 재현 및 수치 저장 (개선 전후 비교의 기준값이므로, 락 적용 전에 먼저 수행)
**마감 범위 제외(11/25~)**: 사장님 결제 내역, 리질리언스 패턴(서킷브레이커·타임아웃·재시도·벌크헤드), 락 순서 최적화 등 동시성 심화.

| 날짜 | 할 일 | 끝났다는 기준 |
| --- | --- | --- |
| 11/17 (화) | 결제 접수(T1) API 완성·테스트, 멱등키 dev 반영 | PR #13 머지, PR #15 제출 |
| 11/18 (수) | T2 결제 본처리: Mock 서비스 구성, `PaymentTxService`, `Payment.complete(...)`, Facade 흐름 재구성 | 정상 요청이 COMPLETED로 끝남 (완료, PR #39) |
| 11/19 (목) | T3 실패 처리(FAILED + failure_code), PAY 에러 코드, T2 통합 테스트(성공·잔액 부족·QR 사용됨·롤백) | 실패 시 롤백되고 FAILED가 기록됨 (구현 완료, PR #56. 잔액 부족·지갑 롤백 검증은 실제 지갑 연동 후 #55) |
| 11/20 (금) | 결제 결과 재조회 API (멱등키 기반) | 응답을 못 받은 키로 결과 조회 가능 |
| 11/21 (토) | 결제 취소 ①: 상태 변경, 지갑 환급, 중복 취소 방지, 본인 가맹점 권한 검증 | 취소 후 잔액이 복원됨 |
| 11/22 (일) | 결제 취소 ②: 캐시백 적립 취소(지급 전/후), 취소 테스트. 지연 시 버퍼 | 취소 엣지 케이스 통과 |
| 11/23 (월) | **구현 마감.** 통합·동시성 테스트(같은 키 동시, 같은 지갑 동시), Mock 서비스 → 실제 서비스 교체(B/C/D 머지된 범위, 이슈 #55), 버그 수정 | 위 4개 기능 동작 |
| **11/24 (화)** | **중간보고** (시연 시나리오, 보고 자료 준비) | |
| 11/25~26 | 사장님 결제 내역 | |
| 11/27 | 동시성 심화: 락 순서(지갑 → QR → 예산), 데드락 점검 | |
| 11/28~12/1 | 타임아웃·재시도·서킷브레이커·벌크헤드, 장애 시나리오 테스트 | |
| 12/2~3 | 통합·버그 수정, 코드 정리, 문서화 | |
| 12/4 | 개발 종료 | |
| 12/5~6 | 발표 준비·리허설 | |
| 12/7 | 발표 | |

**일정 위험**
- 4개 기능을 11/23까지 끝내야 해서 주말(11/21~22)을 포함합니다. 취소가 가장 무거우므로 11/20까지 앞 단계가 밀리지 않아야 합니다.
- 취소는 지갑 환급(`credit`)과 캐시백 취소(`cancel`)가 필요합니다. B/C/D 코드가 없으면 Mock으로 진행하고 11/23에 교체합니다.
- 11/23 마감 시점에 B/C/D의 실제 구현이 머지되지 않았다면 Mock 상태로 중간보고를 시연합니다.

## 5. 팀 합의가 필요한 사항

| # | 내용 | 대상 |
| --- | --- | --- |
| 1 | 지갑·QR·캐시백 호출 시그니처(파라미터·반환·예외). 금액 인자는 `long`. **캐시백은 B의 PR #23(머지 완료)로 확정**: `reserve(CashbackReserveRequest) → CashbackReserveResponse(적립액, 요율, 정책ID, 지급 예정 시각, 제외 사유)`, `cancel(paymentId)`. 지갑·QR은 현재 Mock 기준: `debit(walletId, amount, type, refKey, allowNegative) → DebitResult(walletUsed, cashbackUsed)` (`refKey`는 paymentId, `type`은 C가 정하는 거래 유형, 결제는 `allowNegative=false`), `markUsed(qrToken, merchantId, amount, requestedAt)` (QR 식별은 id가 아니라 토큰, 가맹점·금액 검증과 `requestedAt` 기준 만료 판정은 QR 쪽에서 조건부 UPDATE로 처리, 실패하면 `BusinessException`: QR-001~004). **QR은 D의 이슈 #54가 같은 시그니처로 반영**하기로 해서 확정, 지갑(C)은 `debit` 구현·이슈 확인 필요. 캐시백 계약 타입은 #33에서 `CashbackReserveRequest` / `CashbackReserveResponse`로 이름이 바뀜 | C, D |
| 2 | ~~적립액이 0원인 경우의 `reserve` 처리~~ → **해결**: 예외 없이 0원과 제외 사유를 반환 (PR #23) | B |
| 3 | 락 순서 통일: **결제 행 → 지갑 → QR → 예산**. 결제·취소·환불·지급 배치·탈퇴가 같은 순서를 지킴. 또한 `debit` 등은 호출한 쪽 트랜잭션에 참여(`REQUIRED`)해야 T2 롤백 시 함께 롤백됨 | 전원 |
| 4 | 리질리언스 적용 경계(지갑 호출 지점만 vs 트랜잭션 전체) | C |
| 5 | ~~FAILED 건을 같은 키로 재요청할 때 정책~~ → **확정(A): 재시도 없이 기존 실패 결과 반환.** 다시 결제하려면 프론트가 새 멱등키로 요청 (2-2절). 팀에 공유 필요 | 전원 |
| 6 | PROCESSING에서 멈춘 건(서버 중단, T2와 T3가 모두 실패한 경우)의 정리 정책. 지금은 같은 키로 재요청하면 계속 `COM-004`만 받고, 새 키로는 결제됨 (돈이 새지는 않음). 선택지: 일정 시간 뒤 스케줄러가 FAILED 처리 / 재요청 시점에 오래된 건 정리 / 만료 시각 컬럼 | 전원 |
| 7 | 결제 요청의 `walletId`는 JWT 연동 전까지 본문으로 받음. 연동 후 토큰에서 추출 | C |
| 8 | COM-005~008은 Security(JWT) 쪽에서 `AuthenticationEntryPoint`/`AccessDeniedHandler`로 매핑 필요. 임시 SecurityConfig에서 `/api/payments/**`가 인증을 요구해 수동 호출이 막힘 | C |
| 9 | PAY 에러 코드 목록과 소유 범위: 현재 `PAY-001`(미사용), `PAY-002`(임시, 지갑 코드가 생기면 삭제). 다른 도메인의 실패는 그 도메인 코드를 그대로 기록 (2-4절) | 전원 |
| 10 | 월 예산 ID 위치: B 제안대로 `cashback_earning` 쪽에 두고 `payment`에는 추가하지 않는 방향 (A 스키마가 B에 의존하지 않도록). 이견 없으면 확정 | B |
| 11 | 기획서·역할분담 문서와 현재 설정 불일치 정리 (DB: PostgreSQL 확정, 일정 4주/3주, 소수점 누적 정책 문구) | 전원 |
| 12 | 금액 컬럼 타입: 캐시백 기획서는 `NUMERIC(15,4)` 유지, 결제는 `BIGINT`/`Long`(정수 원). `reserve` 계약이 `long`이므로 컬럼 타입을 맞출지 | B |
| 13 | 영수증의 월 한도 상태: B 기획서 3-4절은 `reserve` 반환값에 포함한다고 되어 있으나 머지된 `CashbackReserveResponse`에는 없음. 반환값에 필드를 추가(B가 후속 PR)할지, `GET /api/cashback/monthly-limit`로 별도 조회할지 | B, C |
| 14 | 결제 1회 한도(100만원): 기획서에는 항목이 없음(충전·보유 한도는 C, QR 금액 상한은 D). 결제 금액은 QR 금액과 같아야 하므로 **D의 QR 상한(100만원)으로 갈음**하고 결제 쪽에는 따로 두지 않는 방향으로 팀에 제안 예정. 별도로 필요하면 `application.yaml` 설정값 하나를 A와 D가 같이 읽는 방식. 갈음하기로 하면 `PAY-001` 삭제 | 전원 (D) |

## 6. 테스트 계획

- 같은 키 재요청: 새 키 → PROCESSING, 같은 키+같은 요청(처리 중) → COM-004, 같은 키+다른 요청 → COM-003, 완료 건 재요청 → 기존 결과 반환 (구현·통과, PR #15)
- 같은 키 + 실패 건 재요청: 재시도 없이 기존 FAILED 결과 반환 (구현·통과, `@Disabled` 해제하고 정책 #5에 맞게 변경)
- 시각 검증: 시계를 고정(`MutableClock`)하고 접수·완료 시각이 그 값으로 저장되는지 (구현·통과)
- 결제 본처리: 성공(COMPLETED), QR 처리 실패 시 롤백 (구현·통과. 실패 시나리오는 `@MockitoBean`으로 QR Mock을 대체). 잔액 부족·지갑 차감 롤백은 실제 지갑 연동 후 (#55)
- 실패 처리 T3 (`PaymentFacadeFailureTest`, `PaymentTest`, 구현·통과):
  - T2가 `BusinessException`이면 FAILED + 그 에러 코드, 일반 예외면 FAILED + `COM-010`
  - `COM-004`는 실패로 기록하지 않고 PROCESSING 유지
  - T2 롤백 뒤에도 T1 행이 남음, 같은 키 재요청은 기존 FAILED 결과 반환(QR 처리는 1번만)
  - T2와 T3가 모두 실패하면 PROCESSING으로 남음, T2 성공이면 T3 호출 안 됨
  - 이미 COMPLETED/FAILED인 건에 T3를 호출해도 상태·첫 실패 코드 유지, `Payment.failure` 단위 테스트(처리 중만 허용, 빈 코드 거부)
- 결제 취소: 중복 취소는 기존 결과 반환, 남의 가맹점 취소 거절, 지급 전/후 캐시백 취소
- 같은 키 동시 요청: 행이 1개만 생성되는지
- 같은 지갑 동시 결제, 같은 QR 동시 결제, 같은 예산 동시 예약: 최종 잔액이 기대값과 일치하는지
- 실패 시 롤백(일부만 반영되는 상태가 없어야 함), 지급 배치와 취소 동시 실행
- 락 없는 결제로 오류를 먼저 재현하고 수치를 저장한 뒤, 락 적용 후 같은 조건으로 비교 (개선 전후 증명)
