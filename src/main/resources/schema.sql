-- 서버가 시작할 때 JPA 가 테이블을 만든 뒤 실행된다. 여러 번 실행해도 결과가 같다.
-- 문장 구분자는 ^^ 이다(application.yaml 의 spring.sql.init.separator). DO 블록 안에 세미콜론이 있어서다.
-- JPA 애너테이션으로는 조건이 붙은(부분) 유니크 인덱스를 만들 수 없어 여기서 만든다.

-- 이메일·전화번호는 활동 중인 계정끼리, 계정 종류(role)별로만 겹치지 않으면 된다
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_role_active
    ON users (email, role) WHERE status = 'ACTIVE'
^^
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_phone_role_active
    ON users (phone, role) WHERE status = 'ACTIVE'
^^
-- 예전의 email, phone 단독 유니크 제약이 남아 있는 DB 에서는 지운다. 제약 이름은 DB 마다 달라서 컬럼으로 찾는다
DO $$
DECLARE
    old_constraint record;
BEGIN
    FOR old_constraint IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'users'::regclass
          AND c.contype = 'u'
          AND (SELECT array_agg(a.attname::text)
               FROM pg_attribute a
               WHERE a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey))
              IN (ARRAY['email'], ARRAY['phone'])
    LOOP
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', old_constraint.conname);
    END LOOP;
END $$
^^
-- 적립 제외 사유에 SELF_PAYMENT(본인 가맹점 결제, #301)가 늘었다. ddl-auto: update 는 기존 체크 제약을 바꾸지 않아서
-- 제약을 지우고 새 값 목록으로 다시 만든다. 제약 이름은 DB 마다 달라서 컬럼으로 찾는다. 여러 번 실행해도 같은 결과다
DO $$
DECLARE
    c record;
BEGIN
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
        WHERE con.conrelid = 'cashback_earning'::regclass
          AND con.contype = 'c'
          AND att.attname = 'exclusion_reason'
    LOOP
        EXECUTE format('ALTER TABLE cashback_earning DROP CONSTRAINT %I', c.conname);
    END LOOP;
    ALTER TABLE cashback_earning ADD CONSTRAINT cashback_earning_exclusion_reason_check
        CHECK (exclusion_reason IN ('NONE', 'SELF_PAYMENT', 'NOT_ELIGIBLE', 'NO_WALLET_USAGE',
                                    'AMOUNT_TOO_SMALL', 'MONTHLY_LIMIT_EXCEEDED', 'BUDGET_EXHAUSTED'));
END $$
^^
