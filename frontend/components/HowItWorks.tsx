"use client";

import { useEffect, useState } from "react";
import SignBoard from "@/components/SignBoard";
import { ICONS } from "@/components/icons";

type Role = "user" | "merchant";
type Step = { title: string; desc: string };

const SCENE_MS = 5200;

const STEPS: Record<Role, Step[]> = {
  user: [
    { title: "데브머니 충전", desc: "모의 계좌에서 원하는 금액만큼 데브머니를 충전해요. 지갑에는 보유 한도까지만 담을 수 있어요." },
    { title: "QR 스캔", desc: "가맹점의 QR을 카메라로 비추면 가맹점과 결제 금액을 바로 읽어요. QR은 3분 안에 사용해야 해요." },
    { title: "결제 전에 미리 확인", desc: "데브머니와 캐시백이 얼마씩 쓰이고, 캐시백이 얼마나 적립될지, 이번 달 적립 한도가 얼마나 남는지를 결제 전에 보여 드려요." },
    { title: "승인하고 영수증으로 확정", desc: "간편비밀번호 6자리로 승인하면 적용된 요율과 지급 예정일이 영수증에 그대로 남아서, 약속한 금액이 바뀌지 않아요." },
    { title: "캐시백 적립", desc: "결제가 끝나면 적립 예정 캐시백이 쌓이고, 정해진 지급일에 지갑으로 들어와요. 이번 달 한도도 함께 보여 줘요." },
  ],
  merchant: [
    { title: "가맹점 등록", desc: "가맹점 이름과 주소를 입력하고 연매출 증빙 서류를 첨부하면 적립 대상 가맹점으로 등록돼요." },
    { title: "QR 만들기", desc: "결제 금액을 입력하면 3분 동안 쓸 수 있는 QR이 만들어져요. 한 번 사용한 QR은 다시 쓸 수 없어요." },
    { title: "결제 완료 확인", desc: "회원이 결제하면 QR 상태가 바로 결제완료로 바뀌고 결제 금액이 표시돼요." },
    { title: "일 · 월 정산 확인", desc: "매출과 취소 금액을 반영한 일 정산과 월 정산을 날짜별로 확인해요." },
  ],
};

const ROLE_LABEL: Record<Role, string> = { user: "회원", merchant: "가맹점주" };

export default function HowItWorks() {
  const [role, setRole] = useState<Role>("user");
  const [scene, setScene] = useState(0);
  const [paused, setPaused] = useState(false);
  const steps = STEPS[role];

  useEffect(() => {
    if (paused) return;
    const id = setTimeout(() => {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      setScene((s) => (s + 1) % STEPS[role].length);
    }, SCENE_MS);
    return () => clearTimeout(id);
  }, [scene, role, paused]);

  const pick = (r: Role) => {
    setRole(r);
    setScene(0);
  };

  return (
    <div>
      <div role="tablist" aria-label="사용자 구분" className="mt-6 inline-flex rounded-xl border border-slate-200 bg-white p-1">
        {(Object.keys(ROLE_LABEL) as Role[]).map((r) => (
          <button
            key={r}
            type="button"
            role="tab"
            id={`how-tab-${r}`}
            aria-selected={role === r}
            aria-controls="how-panel"
            onClick={() => pick(r)}
            className={`rounded-lg px-6 py-2.5 text-[15px] font-bold transition-colors ${role === r ? "bg-brand-600 text-white" : "text-slate-600 hover:bg-slate-100"}`}
          >
            {ROLE_LABEL[r]}
          </button>
        ))}
      </div>

      <div
        id="how-panel"
        role="tabpanel"
        aria-labelledby={`how-tab-${role}`}
        className="mt-8 grid items-center gap-10 md:grid-cols-[1fr_auto]"
        onMouseEnter={() => setPaused(true)}
        onMouseLeave={() => setPaused(false)}
      >
        <ol className="space-y-2">
          {steps.map((s, i) => {
            const on = i === scene;
            return (
              <li key={s.title}>
                <button
                  type="button"
                  onClick={() => setScene(i)}
                  aria-current={on ? "step" : undefined}
                  className={`w-full rounded-xl border p-4 text-left transition-colors ${on ? "border-brand-600 bg-brand-50" : "border-transparent hover:bg-slate-100"}`}
                >
                  <span className="flex items-center gap-3">
                    <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-extrabold ${on ? "bg-brand-600 text-white" : "bg-slate-200 text-slate-600"}`}>{i + 1}</span>
                    <span className="text-lg font-bold">{s.title}</span>
                  </span>
                  {on && (
                    <>
                      <span className="mt-2 block pl-11 leading-relaxed text-slate-600">{s.desc}</span>
                      <span className="ml-11 mt-3 block h-1 overflow-hidden rounded-full bg-brand-100" aria-hidden>{!paused && <span key={`${role}-${scene}`} className="demo-progress block h-full rounded-full bg-brand-600" />}</span>
                    </>
                  )}
                </button>
              </li>
            );
          })}
        </ol>

        <div className="mx-auto w-[300px] shrink-0">
          <div className="relative h-[560px] overflow-hidden rounded-[32px] border-[7px] border-slate-900 bg-white shadow-[0_12px_32px_rgba(22,35,29,0.18)]" aria-label={`${ROLE_LABEL[role]} 화면 시연`}>
            <div key={`${role}-${scene}`} className="flex h-full flex-col">
              {role === "user" ? <UserScene index={scene} /> : <MerchantScene index={scene} />}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Bar({ title }: { title: string }) {
  return <div className="bg-brand-600 px-4 py-3 text-[15px] font-bold text-white">{title}</div>;
}

function Delay({ d, className = "", children }: { d: number; className?: string; children: React.ReactNode }) {
  return <div className={className} style={{ "--d": `${d}s` } as React.CSSProperties}>{children}</div>;
}

function UserScene({ index }: { index: number }) {
  if (index === 0) {
    return (
      <>
        <Bar title="충전" />
        <div className="space-y-3 p-4 text-sm">
          <div className="relative rounded-xl border border-brand-100 bg-brand-50/60 p-3">
            <div className="text-xs text-slate-500">현재 잔액</div>
            <div className="relative h-8">
              <span className="demo-x absolute left-0 top-0 text-xl font-extrabold tabular-nums" style={{ "--d": "3.1s" } as React.CSSProperties}>125,000원</span>
              <span className="demo-a absolute left-0 top-0 text-xl font-extrabold tabular-nums text-blue-600" style={{ "--d": "3.2s" } as React.CSSProperties}>175,000원</span>
            </div>
          </div>
          <div>
            <div className="label">충전 금액</div>
            <div className="field flex items-center justify-end gap-1 !py-3 text-right text-lg font-bold tabular-nums">
              <span className="demo-type" style={{ "--w": "4.2em", "--n": 6, "--t": "1s", "--d": "0.5s" } as React.CSSProperties}>50,000</span>
              <span className="text-sm font-medium text-slate-500">원</span>
            </div>
            <div className="mt-2 flex gap-2">
              {["+10,000", "+50,000", "+100,000"].map((c) => <span key={c} className="chip !px-2.5 !py-1 !text-xs">{c}</span>)}
            </div>
          </div>
          <Delay d={2.4} className="demo-press">
            <div className="btn btn-primary">충전하기</div>
          </Delay>
          <Delay d={3.2} className="demo-pop rounded-lg bg-brand-600 py-2.5 text-center text-[13px] font-bold text-white">50,000원 충전이 완료되었어요</Delay>
        </div>
      </>
    );
  }
  if (index === 1) {
    return (
      <>
        <Bar title="QR 결제" />
        <div className="space-y-3 p-4">
          <div className="relative h-44 overflow-hidden rounded-xl bg-slate-900">
            <svg className="absolute inset-0 h-full w-full" viewBox="0 0 260 176" fill="none" stroke="white" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M40 66V46a8 8 0 0 1 8-8h20M220 66V46a8 8 0 0 0-8-8h-20M40 110v20a8 8 0 0 0 8 8h20M220 110v20a8 8 0 0 1-8 8h-20" />
            </svg>
            <div className="demo-scan demo-x absolute left-[18%] right-[18%] h-0.5 rounded bg-emerald-500" style={{ "--d": "2.6s" } as React.CSSProperties} />
            <div className="absolute inset-[22%] grid grid-cols-7 gap-0.5 opacity-90">
              {Array.from({ length: 49 }, (_, i) => <span key={i} className={(i * 7 + (i >> 2)) % 3 === 0 ? "bg-white" : "bg-white/15"} />)}
            </div>
            <Delay d={2.7} className="demo-pop absolute inset-x-8 bottom-3 rounded-lg bg-emerald-600 py-1.5 text-center text-xs font-bold text-white">QR을 읽었어요</Delay>
          </div>
          <Delay d={3.2} className="demo-a card !p-3.5">
            <div className="font-bold">카페 모닝</div>
            <span className="badge badge-ok mt-1">적립 가능 가맹점</span>
            <div className="mt-2 flex items-baseline justify-between border-t border-slate-100 pt-2">
              <span className="note">결제금액</span>
              <span className="amt-out text-2xl font-extrabold tabular-nums">12,000원</span>
            </div>
          </Delay>
        </div>
      </>
    );
  }
  if (index === 2) {
    return (
      <>
        <Bar title="QR 결제" />
        <div className="space-y-3 p-4">
          <div>
            <div className="note">카페 모닝</div>
            <div className="amt-out text-[28px] font-extrabold tabular-nums">12,000원</div>
          </div>
          <Delay d={0.5} className="demo-a card !p-3">
            <div className="mb-2 flex items-center justify-between">
              <span className="text-[13px] font-bold">결제 미리보기</span>
              <span className="badge">예상</span>
            </div>
            <SignBoard
              label="결제 미리보기"
              rows={[
                { label: "데브머니 사용", value: "9,000원" },
                { label: "캐시백 사용", value: "3,000원" },
                { label: "예상 적립 캐시백", value: "+900원", tone: "pending" },
              ]}
            />
            <dl className="mt-1 text-[13px]">
              <Delay d={1.6} className="demo-a flex justify-between border-t border-slate-100 py-1.5"><dt className="text-slate-500">적용 요율</dt><dd className="font-medium tabular-nums">10%</dd></Delay>
              <Delay d={2.0} className="demo-a flex justify-between border-t border-slate-100 py-1.5"><dt className="text-slate-500">월 적립 한도 잔여</dt><dd className="font-medium tabular-nums">26,800원 → 25,900원</dd></Delay>
            </dl>
            <Delay d={2.4} className="demo-a note mt-1">예상 금액이에요. 결제 접수 시각의 요율로 확정돼요.</Delay>
          </Delay>
        </div>
      </>
    );
  }
  if (index === 3) {
    return (
      <>
        <Bar title="QR 결제" />
        <div className="space-y-3 p-4">
          <div>
            <div className="note">카페 모닝</div>
            <div className="amt-out text-[28px] font-extrabold tabular-nums">12,000원</div>
          </div>
          <Delay d={0.2} className="demo-a text-center">
            <div className="note mb-2">간편비밀번호 6자리를 입력하세요</div>
            <div className="flex justify-center gap-2.5">
              {[0, 1, 2, 3, 4, 5].map((i) => (
                <span key={i} className="relative h-3.5 w-3.5 rounded-full border-2 border-brand-600">
                  <span className="demo-pop absolute -inset-0.5 rounded-full bg-brand-600" style={{ "--d": `${0.7 + i * 0.28}s` } as React.CSSProperties} />
                </span>
              ))}
            </div>
          </Delay>
          <Delay d={2.6} className="demo-pop absolute inset-x-0 bottom-0 top-[44px] z-10 space-y-3 bg-white p-4">
            <div className="flex items-center gap-3">
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">{ICONS.check}</span>
              <div>
                <div className="font-bold">결제 완료</div>
                <div className="amt-out text-xl font-extrabold tabular-nums">12,000원</div>
              </div>
            </div>
            <div className="card !p-3">
              <div className="mb-2 text-[13px] font-bold">확정 영수증</div>
              <SignBoard
                label="결제 분기"
                rows={[
                  { label: "데브머니 사용", value: "9,000원" },
                  { label: "캐시백 사용", value: "3,000원" },
                  { label: "적립 캐시백", value: "+900원", tone: "pending" },
                ]}
              />
              <dl className="mt-1 text-[13px]">
                <div className="flex justify-between border-t border-slate-100 py-1.5"><dt className="text-slate-500">적용 요율</dt><dd className="font-medium tabular-nums">10%</dd></div>
                <div className="flex justify-between border-t border-slate-100 py-1.5"><dt className="text-slate-500">지급 예정일</dt><dd className="font-medium tabular-nums">2026-11-27</dd></div>
              </dl>
              <p className="note mt-1">소수점 이하는 버림 · 접수 시각 기준 요율로 확정</p>
            </div>
          </Delay>
        </div>
      </>
    );
  }
  return (
    <>
      <Bar title="내 캐시백" />
      <div className="space-y-3 p-4">
        <div className="card !p-4">
          <div className="note">이번 달 캐시백</div>
          <div className="amt-cb text-[28px] font-extrabold tabular-nums">3,200원</div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-200">
            <div className="demo-fill h-full rounded-full bg-emerald-600" style={{ "--from": "10%", "--to": "14.6%", "--t": "1.4s", "--d": "1.4s" } as React.CSSProperties} />
          </div>
          <div className="note mt-1.5">이번 달 적립 한도 중 적립 예정 포함</div>
        </div>
        <Delay d={0.6} className="demo-a flex items-center justify-between rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-3">
          <div>
            <div className="text-sm font-bold">카페 모닝</div>
            <div className="note">결제 12,000원 × 10% · 지급 예정 11.27</div>
          </div>
          <span className="amt-wait font-extrabold tabular-nums">+900</span>
        </Delay>
        <Delay d={1.2} className="demo-a rounded-xl bg-emerald-50 px-3.5 py-3 text-[13px] font-medium text-emerald-800">
          정해진 지급일에 캐시백이 지갑으로 들어와요.
        </Delay>
      </div>
    </>
  );
}

function MerchantScene({ index }: { index: number }) {
  if (index === 0) {
    return (
      <>
        <Bar title="가맹점 등록" />
        <div className="space-y-3 p-4 text-sm">
          <div>
            <div className="label">가맹점명</div>
            <div className="field !py-2"><span className="demo-type" style={{ "--w": "5.2em", "--n": 5, "--t": "0.9s", "--d": "0.4s" } as React.CSSProperties}>카페 모닝</span></div>
          </div>
          <div>
            <div className="label">주소</div>
            <div className="field !py-2"><span className="demo-type" style={{ "--w": "11em", "--n": 11, "--t": "1.2s", "--d": "1.5s" } as React.CSSProperties}>서울 ○○구 ○○로 12</span></div>
          </div>
          <Delay d={2.9} className="demo-a rounded-xl border border-slate-200 p-3">
            <div className="text-[13px] font-bold">캐시백 적립 대상 증빙</div>
            <div className="mt-1.5 inline-flex items-center gap-1.5 rounded-lg bg-blue-50 px-2.5 py-1.5 text-xs font-bold text-blue-700">{ICONS.receipt}연매출 증빙 서류.pdf</div>
          </Delay>
          <Delay d={3.7} className="demo-pop rounded-lg bg-emerald-50 px-3 py-2.5 text-[13px] font-bold text-emerald-800">증빙 첨부됨 → 적립 대상 가맹점으로 등록돼요</Delay>
        </div>
      </>
    );
  }
  if (index === 1) {
    return (
      <>
        <Bar title="QR 생성" />
        <div className="space-y-3 p-4">
          <div>
            <div className="label">결제 금액</div>
            <div className="field flex items-center justify-end gap-1 !py-3 text-lg font-bold tabular-nums">
              <span className="demo-type" style={{ "--w": "3.6em", "--n": 6, "--t": "0.9s", "--d": "0.4s" } as React.CSSProperties}>12,000</span>
              <span className="text-sm font-medium text-slate-500">원</span>
            </div>
          </div>
          <Delay d={1.6} className="demo-press"><div className="btn btn-primary">QR 생성</div></Delay>
          <Delay d={2.1} className="demo-pop card text-center">
            <div className="mx-auto grid h-36 w-36 grid-cols-7 gap-0.5 rounded-lg border-2 border-brand-600 p-2">
              {Array.from({ length: 49 }, (_, i) => <span key={i} className={(i * 7 + (i >> 2)) % 3 === 0 ? "bg-slate-800" : "bg-slate-100"} />)}
            </div>
            <p className="mt-2 text-sm">남은 시간 <b className="tabular-nums">02:59</b></p>
          </Delay>
        </div>
      </>
    );
  }
  if (index === 2) {
    return (
      <>
        <Bar title="QR 생성" />
        <div className="relative space-y-3 p-4">
          <Delay d={0.2} className="demo-a card text-center">
            <div className="mx-auto grid h-36 w-36 grid-cols-7 gap-0.5 rounded-lg border-2 border-brand-600 p-2" style={{ opacity: 0.35 }}>
              {Array.from({ length: 49 }, (_, i) => <span key={i} className={(i * 7 + (i >> 2)) % 3 === 0 ? "bg-slate-800" : "bg-slate-100"} />)}
            </div>
          </Delay>
          <div>
            <div className="label">QR 상태</div>
            <div className="flex gap-2">
              <span className="chip chip-on demo-x flex-1 !px-0 text-center" style={{ "--d": "1.4s" } as React.CSSProperties}>대기중</span>
              <span className="chip chip-on demo-pop flex-1 !px-0 text-center" style={{ "--d": "1.5s" } as React.CSSProperties}>결제완료</span>
              <span className="chip flex-1 !px-0 text-center">만료</span>
            </div>
          </div>
          <Delay d={1.5} className="demo-pop rounded-xl bg-emerald-600 p-4 text-center text-white">
            <div className="text-sm font-medium text-white/85">QR 결제 완료</div>
            <div className="text-2xl font-extrabold tabular-nums">12,000원</div>
            <div className="mt-0.5 text-xs text-white/85">○○○님 · 11.21 11:32</div>
          </Delay>
        </div>
      </>
    );
  }
  return (
    <>
      <Bar title="매출 · 정산" />
      <div className="space-y-3 p-4">
        <div className="flex gap-2">
          <span className="chip flex-1 !px-0 text-center">결제 내역</span>
          <span className="chip chip-on flex-1 !px-0 text-center">일 정산</span>
          <span className="chip flex-1 !px-0 text-center">월 정산</span>
        </div>
        <Delay d={0.3} className="demo-a card !p-3.5">
          <div className="flex items-center justify-between"><span className="font-bold">2026.11.17 일 정산</span><span className="badge badge-ok">생성됨</span></div>
          <dl className="mt-2 space-y-1 text-sm tabular-nums">
            <div className="flex justify-between"><dt>결제</dt><dd>340,000원</dd></div>
            <div className="flex justify-between"><dt>− 취소</dt><dd className="amt-out">20,000원</dd></div>
            <div className="flex justify-between border-t border-slate-200 pt-1 font-bold"><dt>= 순매출</dt><dd className="amt-in">320,000원</dd></div>
          </dl>
        </Delay>
        {[["11.16", "410,000원"], ["11.15", "285,000원"], ["11.13", "198,000원"]].map(([d, v], i) => (
          <Delay key={d} d={1.2 + i * 0.5} className="demo-a flex justify-between border-t border-slate-200 px-1 py-2 text-sm tabular-nums">
            <span>{d}</span>
            <b className="font-bold">{v}</b>
          </Delay>
        ))}
      </div>
    </>
  );
}
