# DevPay (데브페이)

> 모의 계좌로 체험하는 믿을 수 있는 지역화폐 결제

선불 지갑(데브머니)으로 QR 결제를 하고, 결제액의 일부를 캐시백으로 적립해 주는 **모의 간편결제 서비스**입니다.
실제 은행·PG 연동 없이, 돈이 움직이는 모든 흐름의 **정합성(동시성, 멱등성, 원장과 잔액의 일치)** 을 직접 구현하고 검증하는 것을 목표로 합니다.

- 프로그래머스 데브코스 백엔드 12기 14회차 / 6팀 (HOT6) 파이널 프로젝트
- 개발 기간: 2026.11.11 ~ 2026.12.4 (발표 12.7)

## 해결하려는 문제

- 결제 실패 시 사유와 다음 행동 안내가 부족하다.
- 캐시백 적립 대상 가맹점인지 결제 전에 알기 어렵다.
- 가맹점 이름 검색이 불편하고, 이번 달 사용·캐시백 내역을 한눈에 보기 어렵다.
- 동시 요청이나 중복 요청에서도 **잔액이 틀리지 않는** 결제 도메인을 만들어 보고 싶다.

## 주요 기능

| 영역 | 내용 |
|---|---|
| 회원·지갑 | 회원가입(모의 계좌 100만원 지급), 로그인, 간편 비밀번호(6자리, 5회 실패 시 잠금), 충전, 환불, 거래 내역, 탈퇴 정산 |
| 가맹점·QR | 가맹점 등록과 고정 QR 발급, 가맹점 이름 검색, 결제별(동적) QR 생성(3분 만료), QR 스캔·상태 조회 |
| 결제 | 멱등키 기반 중복 결제 방지, 지갑 비관적 락, 결제 결과 재조회(자동 30초), 가맹점주 전액 취소 |
| 캐시백·예산 | 기간별 요율 정책, 소수점 누적 적립, 결제 3일 후 지급 배치, 월 한도(3만원), 연/월 예산 관리와 적립 중단 |
| 정산·대사 | 매일 00:10 일 정산(마이너스 이월 포함), 매일 00:30 원장 대사 7종 |

자세한 기능 명세는 [docs/project-spec.md](docs/project-spec.md)를 참고하세요.

## 핵심 설계 포인트

- **원장은 INSERT만**: 잔액 변경은 거래 단위로 추가만 하고, 취소는 새 행으로 기록합니다.
- **멱등키**: 같은 요청은 한 번만 처리하고, 같은 키에 다른 내용이면 거절합니다.
- **동시성 제어**: 같은 지갑의 동시 결제, 같은 QR의 동시 사용, 같은 예산의 동시 예약을 락과 조건부 UPDATE로 처리합니다.
- **접수 시각 기준 정책**: 요청을 서버가 최초 접수한 시각의 캐시백 정책을 결제 기록에 함께 저장합니다.
- **원장 대사**: 매일 7종의 대사로 계좌·지갑·정산 금액의 일치를 검증합니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 25, Spring Boot 4.1.2, Spring Security, Spring Data JPA |
| Database | PostgreSQL 17 (Docker Compose) |
| Build | Gradle |
| Test | JUnit 5, Spring Boot Test |
| Infra | AWS EC2 + Docker (백엔드), Vercel (프론트엔드) |
| 예정 | k6(부하 테스트), Redis(정책 캐시·멱등키 후보), GitHub Actions, Next.js(프론트엔드) |

## 시작하기

### 사전 준비

- JDK 25
- Docker Desktop

### 실행

```bash
# 1. 저장소 클론
git clone https://github.com/prgrms-be-devcourse/NBE12-14-final-HOT6.git
cd NBE12-14-final-HOT6

# 2. 데이터베이스 실행 (PostgreSQL)
docker compose up -d

# 3. .env 파일 만들기 (저장소에 올라가지 않음). 무작위 JWT 키를 만들어 한 줄로 넣는다.
#    이미 .env 가 있으면 건드리지 말고 JWT_SECRET 줄만 추가한다.
[ -f .env ] || echo "JWT_SECRET=$(openssl rand -base64 48)" > .env

# 4. 애플리케이션 실행
./gradlew bootRun
```

Windows Git Bash에서는 `./gradlew`, 명령 프롬프트에서는 `gradlew.bat`을 사용합니다.
IntelliJ에서는 `Nbe1214FinalHot6Application`을 실행해도 됩니다(작업 디렉터리가 프로젝트 폴더여야 `.env`를 읽습니다).

### 환경 변수

| 이름 | 설명 | 기본값 (로컬 개발용) |
|---|---|---|
| `DB_PASSWORD` | PostgreSQL 비밀번호 | `devpay1234` |
| `JWT_SECRET` | JWT 서명 키(32바이트 이상) | 없음. 필수이며 없으면 시작할 때 실패합니다 |

로컬에서는 프로젝트 폴더의 `.env` 파일에서 읽습니다(`KEY=값` 한 줄씩, 따옴표·공백 없이).
```text
JWT_SECRET=여기에_키
```
`JWT_SECRET`이 없으면 서버가 시작되지 않습니다.

로컬 DB 기본 접속 정보: `localhost:5432` / DB `devpay` / 사용자 `devpay`.
배포 환경의 비밀번호와 비밀키는 저장소에 커밋하지 않고 GitHub Secrets나 서버 환경변수로 관리합니다.

### 유용한 명령

```bash
docker compose ps          # DB 상태 확인
docker compose down        # DB 중지 (데이터 유지)
docker compose down -v     # DB 중지 및 데이터 초기화
./gradlew test             # 테스트 실행 (DB가 떠 있어야 함. @IntegrationTest를 붙인 테스트는 devpay_test DB를 자동 생성)
```

## 프로젝트 구조

```
src/main/java/com/back/nbe1214finalhot6
├── auth, wallet        # 회원·인증·지갑
├── merchant, qr, settlement   # 가맹점·QR·정산
├── payment, common     # 결제, 멱등키·공통 에러 코드
└── cashback            # 캐시백 정책·예산
```

## 문서

| 문서 | 내용 |
|---|---|
| [docs/project-spec.md](docs/project-spec.md) | 기획서: 기능 목록, 캐시백·예산 정책, 대사 7종, ERD 변경 사항 |
| [docs/team-roles.md](docs/team-roles.md) | 역할 분담, 담당 간 연동, 주차별 일정 |

## 팀 구성

| 담당 | 영역 |
|---|---|
| A | 결제와 동시성 (멱등키, 결제, 취소) |
| B | 캐시백·예산·정책 |
| C | 회원·지갑 |
| D | 가맹점·QR·정산, AWS 배포 |

## 협업 규칙

### 워크플로우

이슈 등록 → 브랜치 생성 → 커밋 → PR → 리뷰 → 머지(이슈 자동 종료)

- **브랜치 구조**: `main`(배포·제출용, 직접 푸시 금지) → `dev`(통합 개발) → `타입/#이슈번호-작업요약`
- **이슈 제목**: `[Feat] 내용`, `[Fix] 내용`
- **브랜치 이름**: `feat/#152-habit-register`
- **커밋 메시지**: `타입: 내용` (예: `feat: 벌칙인증 구현`), 잘게 쪼개서 커밋
- **PR 제목**: `타입(도메인): 내용`, 본문에 `Closes #이슈번호`, 코드 최대 300줄(권장 150~200줄)
- **리뷰**: 한 명이 승인하고, 모든 팀원이 댓글을 남깁니다.
- **머지**: GitHub에서 Squash and merge
- **타입**: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

### 작업 순서

```bash
git switch dev && git pull origin dev     # 1. dev 최신화
git switch -c feat/#10-create-group       # 2. 작업 브랜치 생성
# 3. 작업 후 커밋, dev를 병합해 충돌을 미리 해결
# 4. 푸시하고 dev로 PR 생성
```

### 코드 컨벤션

- Google Java Style Guide를 따릅니다. (IntelliJ 플러그인 설치)
- 스키마 변경은 PR로만 합니다.
