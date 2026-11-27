-- 배포 DB의 계좌 원장 종류와 대사 종류 체크 제약을 바꾸는 SQL (EC2에서 psql 로 실행)
--
-- 왜 필요한가: 계좌 원장 종류에 SETTLEMENT_PAYOUT(월 정산 입금, #277)이, 대사 종류에 MERCHANT_CLOSURE(해지 대사, #283)가 늘었는데,
-- ddl-auto: update 는 기존 체크 제약을 바꾸지 않는다.
-- 실행하지 않으면 월 정산 지급과 해지 정산이 실패하고(계좌 원장 저장 거절), 해지 대사 결과를 저장하지 못한다.
--
-- 새 버전을 배포하기 전이나 직후에 한 번 실행한다. 여러 번 실행해도 안전하다. 로컬 DB를 초기화하지 않은 경우에도 똑같이 실행한다.

begin;

-- 제약 이름이 환경마다 다를 수 있어, type 컬럼에 걸린 체크 제약을 이름과 상관없이 모두 지운다
do $$
declare c record;
begin
  for c in
    select con.conrelid::regclass::text as tbl, con.conname
    from pg_constraint con
    join pg_attribute att on att.attrelid = con.conrelid and att.attnum = any (con.conkey)
    where con.conrelid in ('account_transaction'::regclass, 'reconciliation_result'::regclass)
      and con.contype = 'c' and att.attname = 'type'
  loop
    execute format('alter table %s drop constraint %I', c.tbl, c.conname);
  end loop;
end $$;

alter table account_transaction add constraint account_transaction_type_check
  check (type in ('SIGNUP_BONUS', 'CHARGE', 'REFUND', 'SETTLEMENT_PAYOUT'));

alter table reconciliation_result add constraint reconciliation_result_type_check
  check (type in ('ACCOUNT_BALANCE', 'DEVMONEY_BALANCE', 'CASHBACK_BALANCE', 'CASHBACK_EARNING', 'CASHBACK_BUDGET',
                  'CHARGE_TRANSFER', 'REFUND_TRANSFER', 'DAILY_NET_SALES', 'SETTLEMENT_CARRY', 'MONTHLY_PAYOUT',
                  'MERCHANT_CLOSURE'));

commit;

-- 확인: 각 표의 type 제약은 새 값(SETTLEMENT_PAYOUT, MERCHANT_CLOSURE)을 포함한 한 건이어야 한다
select conrelid::regclass as tbl, conname, pg_get_constraintdef(oid) from pg_constraint
where conrelid in ('account_transaction'::regclass, 'reconciliation_result'::regclass) and contype = 'c';
