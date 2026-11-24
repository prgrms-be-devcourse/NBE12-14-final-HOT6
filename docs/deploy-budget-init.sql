-- 배포 DB에 올해 캐시백 예산을 넣는 SQL (EC2에서 psql 로 실행)
--
-- 왜 필요한가: 예산이 없으면 모든 결제의 캐시백 적립이 "예산 소진"으로 제외된다.
-- 새 버전이 배포되면 서버가 시작할 때 연 예산은 자동으로 들어가지만(CashbackInitData),
-- 월 예산은 운영자가 정책·예산 화면에서 배정해야 한다. 아래는 새 버전이 배포되기 전까지의 임시 조치다.
--
-- 여러 번 실행해도 안전하다(이미 있으면 건드리지 않는다). 운영자가 화면에서 넣은 값은 덮어쓰지 않는다.

-- 0) 먼저 지금 상태를 본다
select budget_year, total_amount, reserved_amount, paid_amount from cashback_annual_budget;
select budget_year, budget_month, allocated_amount, contingency_amount, base_rate, status
  from cashback_monthly_budget order by budget_year, budget_month;

begin;

-- 1) 2026년 연 예산 1억원
insert into cashback_annual_budget
  (budget_year, total_amount, carried_in_amount, carried_out_amount, reserved_amount, paid_amount)
values (2026, 100000000, 0, 0, 0, 0)
on conflict (budget_year) do nothing;

-- 2) 2026년 11월·12월 월 예산 각 1,000만원 (기본 요율 10%, 대비금은 배정의 10%)
insert into cashback_monthly_budget
  (budget_year, budget_month, allocated_amount, carried_in_amount, carried_out_amount,
   contingency_amount, reserved_amount, paid_amount, base_rate, status)
values
  (2026, 11, 10000000, 0, 0, 1000000, 0, 0, 0.1000, 'ACTIVE'),
  (2026, 12, 10000000, 0, 0, 1000000, 0, 0, 0.1000, 'ACTIVE')
on conflict (budget_year, budget_month) do nothing;

-- 3) 결과 확인 (연 예산 1건, 월 예산 11·12월 2건이 보여야 한다)
select budget_year, total_amount from cashback_annual_budget where budget_year = 2026;
select budget_month, allocated_amount, contingency_amount, base_rate, status
  from cashback_monthly_budget where budget_year = 2026 order by budget_month;

commit;

-- 주의
-- * 이미 "예산 소진"으로 제외된 결제는 소급해서 적립되지 않는다. 이후 결제부터 적립된다.
-- * 이후 월 예산은 운영자 화면(정책 · 예산 관리)에서 바꾼다. 결제가 시작된 달은 배정을 바꿀 수 없다.
