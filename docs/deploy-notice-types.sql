-- 배포 DB의 공지 종류 체크 제약을 바꾸는 SQL (EC2에서 psql 로 실행)
--
-- 왜 필요한가: 공지 종류에 LIMIT_CHANGE(한도 변경)가 늘었는데, ddl-auto: update 는 기존 체크 제약을 바꾸지 않는다.
-- 실행하지 않으면 공지를 함께 하는 한도 변경과 예약이 실패한다.
--
-- 새 버전을 배포하기 전이나 직후에 한 번 실행한다. 여러 번 실행해도 안전하다.

begin;

-- 제약 이름이 환경마다 다를 수 있어, type 컬럼에 걸린 체크 제약을 이름과 상관없이 모두 지운다
do $$
declare c record;
begin
  for c in
    select con.conname
    from pg_constraint con
    join pg_attribute att on att.attrelid = con.conrelid and att.attnum = any (con.conkey)
    where con.conrelid = 'cashback_notice'::regclass and con.contype = 'c' and att.attname = 'type'
  loop
    execute format('alter table cashback_notice drop constraint %I', c.conname);
  end loop;
end $$;

alter table cashback_notice add constraint cashback_notice_type_check
  check (type in ('POLICY_CHANGE', 'BUDGET_NOTICE', 'EMERGENCY_STOP', 'STOP_CANCELED', 'LIMIT_CHANGE'));

commit;

-- 확인: type 제약은 LIMIT_CHANGE 를 포함한 한 건이어야 한다
select conname, pg_get_constraintdef(oid) from pg_constraint
where conrelid = 'cashback_notice'::regclass and contype = 'c';
