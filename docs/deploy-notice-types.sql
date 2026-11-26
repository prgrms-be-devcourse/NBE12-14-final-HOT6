-- 배포 DB의 공지 종류 체크 제약을 바꾸는 SQL (EC2에서 psql 로 실행)
--
-- 왜 필요한가: 공지 종류에 LIMIT_CHANGE(한도 변경)가 늘었는데, ddl-auto: update 는 기존 체크 제약을 바꾸지 않는다.
-- 실행하지 않으면 공지를 함께 하는 한도 변경과 예약이 실패한다.
--
-- 새 버전을 배포하기 전이나 직후에 한 번 실행한다. 여러 번 실행해도 안전하다.

begin;

alter table cashback_notice drop constraint if exists cashback_notice_type_check;
alter table cashback_notice add constraint cashback_notice_type_check
  check (type in ('POLICY_CHANGE', 'BUDGET_NOTICE', 'EMERGENCY_STOP', 'STOP_CANCELED', 'LIMIT_CHANGE'));

commit;

-- 확인
select pg_get_constraintdef(oid) from pg_constraint where conname = 'cashback_notice_type_check';
