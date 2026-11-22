# AWS 배포 가이드

EC2 1대에서 Docker로 nginx, 백엔드, 프론트엔드를 실행하고, DB는 RDS PostgreSQL을 사용합니다. 인프라는 Terraform으로 만들고, 서버 안의 구성(Docker, nginx, 인증서)은 직접 설정합니다.

```
인터넷 → [탄력적 IP] → EC2 (nginx 80/443)
                        ├─ /api, /actuator/health → backend:8080 → RDS(5432)
                        └─ 그 외 → frontend:3000
```

| 자원 | 사양 |
|---|---|
| EC2 | t3.medium 1대, Ubuntu 24.04, gp3 20GB, 스왑 2GB |
| RDS | PostgreSQL 17, db.t4g.micro (부족하면 small), gp3 20GB, 단일 AZ, 비공개 서브넷 |
| 네트워크 | VPC `10.6.0.0/16`, 공개 서브넷 1개, 비공개 서브넷 2개, NAT 없음 |
| 보안 그룹 | web-sg(80, 443은 전체, 22는 내 IP), db-sg(5432는 web-sg만) |

모든 리소스에 `Team: devcos-team06` 태그가 붙습니다. 비밀번호, 키, 상태 파일은 저장소에 올리지 않습니다. 아래의 값은 모두 예시입니다.

## 1. 사전 준비 (처음 한 번)

1. 콘솔에서 키 페어를 만들고 `.pem`을 저장소 밖(`~/.ssh/`)에 보관합니다. 이름은 `devcos-team06-key`이고 `Team` 태그를 붙입니다.
2. 콘솔에서 탄력적 IP를 할당합니다. 태그는 `Name = devcos-team06-eip`, `Team = devcos-team06`입니다. 키 페어와 탄력적 IP는 `terraform destroy`를 해도 남도록 Terraform이 아니라 콘솔에서 만들고, Terraform은 이름으로 찾아서 연결만 합니다.
3. Terraform과 AWS CLI를 설치하고, 내 터미널에서 `aws configure --profile devcos-team06`로 접속 정보를 설정합니다.
4. 루트 `.gitignore`에 `*.pem`, `*.tfstate`, `*.tfstate.*`, `.terraform/`, `*.tfvars`가 들어 있는지 확인합니다.

## 2. 인프라 생성 (Terraform)

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars   # my_ip_cidr를 내 공인 IP/32로 수정
read -rs -p "DB 비밀번호: " TF_VAR_db_password; export TF_VAR_db_password; echo
terraform init
terraform plan
terraform apply
```

- `plan`에서 변경과 삭제가 0개이고, 모든 리소스 이름에 `devcos-team06`이 들어 있는지 확인한 뒤 `apply`합니다. 공유 계정이므로 다른 팀 자원이 대상이 아니어야 합니다.
- RDS 생성에 10~15분이 걸립니다.
- 완료되면 `server_ip`, `ssh_command`, `db_endpoint`가 출력됩니다.
- `terraform.tfstate`는 저장소에 올리지 않고, 저장소 밖에 백업해 둡니다.
- DB 비밀번호는 `terraform apply`를 다시 실행할 때마다 같은 값으로 입력해야 합니다.

## 3. 서버 확인

```bash
ssh -i ~/.ssh/devcos-team06-key.pem ubuntu@<탄력적 IP>
cloud-init status --wait        # status: done
swapon --show                   # /swapfile 2G
docker --version
docker compose version
timeout 5 bash -c '</dev/tcp/<RDS 주소>/5432' && echo "연결 가능"
```

## 4. 앱 기동 (HTTP)

서버에서 저장소를 받고 `.env`를 작성합니다. `.env`는 서버 안에서만 작성하고 권한을 600으로 줄입니다.

```bash
git clone https://github.com/prgrms-be-devcourse/NBE12-14-final-HOT6.git
cd NBE12-14-final-HOT6
cp .env.example .env
nano .env        # SPRING_DATASOURCE_URL과 DB_PASSWORD를 실제 값으로 수정
chmod 600 .env
```

| 변수 | 설명 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<RDS 주소>:5432/devpay` |
| `DB_PASSWORD` | Terraform에 넣은 DB 비밀번호와 같은 값 |
| `SPRING_JPA_SHOW_SQL` | 운영에서는 `false` |

메모리가 작으므로 백엔드와 프론트를 한 번에 하나씩 빌드합니다.

```bash
docker compose -f docker-compose.prod.yml build backend
docker compose -f docker-compose.prod.yml build frontend
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

`curl http://<탄력적 IP>/actuator/health`가 `{"status":"UP"}`이면 정상입니다.

## 5. HTTPS 적용 (DuckDNS + certbot)

1. duckdns.org에서 서브도메인을 만들고 IP를 탄력적 IP로 지정합니다. 토큰은 서버에 두지 않습니다.
2. `deploy/nginx/default.conf`의 `server_name`과 인증서 경로의 도메인이 내 도메인과 같은지 확인합니다.
3. 인증서 파일이 없으면 nginx가 시작되지 않으므로, 처음에는 443 서버 블록 없이 80번 서버 블록과 `/.well-known/acme-challenge/` 경로만 둔 설정으로 기동합니다.
4. 스테이징(연습)으로 발급해서 절차를 확인한 뒤, 삭제하고 실제 인증서를 발급합니다.

```bash
# 연습 발급
sudo docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v /var/www/certbot:/var/www/certbot \
  certbot/certbot certonly --webroot -w /var/www/certbot -d <도메인> \
  --staging --register-unsafely-without-email --agree-tos

# 연습 인증서 삭제 후 실제 발급
sudo docker run --rm -v /etc/letsencrypt:/etc/letsencrypt \
  certbot/certbot delete --cert-name <도메인> --non-interactive
sudo docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v /var/www/certbot:/var/www/certbot \
  certbot/certbot certonly --webroot -w /var/www/certbot -d <도메인> \
  --register-unsafely-without-email --agree-tos
```

5. 443 서버 블록이 들어 있는 설정으로 nginx를 다시 시작합니다.

```bash
docker compose -f docker-compose.prod.yml restart nginx
```

6. 90일마다 만료되므로 매일 새벽 3시에 갱신을 시도하도록 예약합니다. 먼저 `certbot renew --dry-run`으로 연습한 뒤 등록합니다.

```bash
(sudo crontab -l 2>/dev/null; echo '0 3 * * * docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v /var/www/certbot:/var/www/certbot certbot/certbot renew --quiet && docker exec devpay-nginx nginx -s reload') | sudo crontab -
```

인증서는 서버의 `/etc/letsencrypt`에만 보관하고 저장소에 올리지 않습니다.

## 6. 재배포

서버에서 `deploy.sh`를 실행하면 코드 받기, 빌드, 컨테이너 재시작이 한 번에 진행됩니다.

```bash
./deploy.sh <브랜치>     # 기본값은 main
```

## 7. 접속 확인

| 확인 | 주소 | 기대 결과 |
|---|---|---|
| 헬스 체크 | `https://<도메인>/actuator/health` | `{"status":"UP"}` |
| 프론트 | `https://<도메인>/` | 메인 화면, 주소창에 자물쇠 |
| HTTP | `http://<도메인>/` | HTTPS로 자동 이동 |

## 문제 해결

| 증상 | 확인할 것 |
|---|---|
| 서버 SSH 접속이 안 됨 | 내 IP가 바뀌었는지 확인하고, `terraform.tfvars`의 `my_ip_cidr`를 고친 뒤 `terraform apply`를 다시 실행합니다. |
| 백엔드가 계속 재시작됨 | `logs backend`에서 DB 연결 오류를 확인합니다. `.env`의 주소와 비밀번호, RDS 보안 그룹을 점검합니다. |
| nginx가 계속 재시작됨 | `logs nginx`의 `[emerg]` 줄을 확인합니다. 인증서 파일이 없으면 발급이 먼저 필요합니다. |
| 빌드 중 `Killed` 또는 종료 코드 137 | 메모리 부족이므로 `free -h`로 스왑이 켜져 있는지 확인하고, 빌드를 한 번에 하나씩 실행합니다. |
| 프론트의 API 호출이 실패함 | 프론트의 `BACKEND_URL`은 빌드할 때 확정되므로, 변경하면 프론트를 다시 빌드합니다. |

## 정리 (삭제)

불필요해진 자원은 중지가 아니라 삭제해야 완전히 정리됩니다. 순서는 다음과 같습니다.

1. `infra`에서 `terraform plan -destroy`로 대상이 우리 팀 리소스뿐인지 확인한 뒤 `terraform destroy`를 실행합니다. EC2, RDS(최종 스냅샷 없음), 네트워크, 보안 그룹이 삭제됩니다.
2. 콘솔에서 탄력적 IP를 해제하고, 키 페어를 삭제합니다.
3. 남은 스냅샷과 볼륨이 없는지 확인합니다.

## 알려진 사항

- `ddl-auto: update`를 서버에서 유지하고 있습니다. 마이그레이션 도구가 도입되면 `validate`로 바꿉니다.
- **인증 없는 API가 외부에 열려 있습니다.** 기존 `SecurityConfig`가 가맹점, QR, 결제, 회원가입, 운영자 API를 인증 없이 허용하고, nginx가 `/api/`를 그대로 전달합니다. 인증 기능(COM-03)이 합쳐지기 전까지의 임시 상태이므로, 실제 개인정보나 계좌 정보를 입력하지 않습니다.
- nginx에서 `/api/admin` 경로를 403으로 차단하고 있습니다. 인증 기능이 합쳐지면 이 차단을 제거합니다. 결제, 가맹점, QR은 화면이 사용하는 경로라서 nginx로 막지 않습니다.
- 운영 환경에서는 Swagger(API 명세)를 끄고, 프론트의 개발용 화면(`/dev`)도 nginx에서 막습니다. API 명세는 로컬에서 앱을 실행해 `http://localhost:8080/swagger-ui/index.html`로 확인합니다.
- `ProdSecurityConfig`는 헬스 체크를 열기 위한 임시 설정입니다. 인증 기능이 합쳐지면 `SecurityConfig`로 통합하고 삭제합니다.
- 상태 파일은 로컬에 보관합니다. 팀 공유가 필요해지면 S3 버킷으로 옮깁니다.
