import { type PolicyBasis, type PolicyHistory } from "@/lib/adminApi";
import { pctText } from "@/lib/cashbackApi";
import { won } from "@/lib/format";

export type BasisValues = Omit<PolicyBasis, "previousRate" | "year" | "month" | "modelVersion">;

const money = (v: number | null) => (v == null ? "-" : won(v));
// 날짜가 없으면 이번 달 안에는 닿지 않는다는 뜻이다
const date = (v: string | null) => (v ? `${Number(v.slice(5, 7))}월 ${Number(v.slice(8, 10))}일` : "이번 달 안 닿음");

// 근거의 예산·예측 값. 저장 전 미리보기와 저장한 이력이 같은 모양으로 보여 준다
export function BasisGrid({ basis: b }: { basis: BasisValues }) {
  return (
    <div className="grid gap-3 md:grid-cols-2">
      <Group title="이번 달 예산" rows={[
        ["남은 예산", money(b.remainingAmount), true],
        ["집행 완료", money(b.paidAmount)],
        ["예약 · 확보", money(b.reservedAmount)],
        ["월 배정", money(b.allocatedAmount)],
        ["대비금", money(b.contingencyAmount)],
      ]} />
      <Group title="소진 예측" rows={[
        ["대비금 도달일", date(b.noticeLineDate), true],
        ["최근 7일 하루 평균", b.dailyDemand == null ? "-" : won(Math.round(b.dailyDemand))],
        ["소진일 · 수요가 빠를 때", date(b.fastExhaustionDate)],
        ["소진일 · 중간", date(b.midExhaustionDate)],
        ["소진일 · 느릴 때", date(b.slowExhaustionDate)],
      ]} />
    </div>
  );
}

// 항목 이름은 왼쪽, 값은 오른쪽에 맞춘 한 묶음. 세 번째 값이 true 인 줄은 크게 보여 준다
function Group({ title, rows }: { title: string; rows: [string, string, boolean?][] }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white" aria-label={title}>
      <h4 className="border-b border-slate-200 px-3 py-2 text-[13px] font-bold text-brand-700">{title}</h4>
      <dl className="divide-y divide-slate-100">
        {rows.map(([k, v, main]) => (
          <div key={k} className="flex items-center justify-between gap-3 px-3 py-2">
            <dt className={main ? "text-sm font-bold" : "text-[13px] text-slate-600"}>{k}</dt>
            <dd className={`text-right tabular-nums ${main ? "text-lg font-extrabold" : "text-sm font-medium"}`}>{v}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

// 요율을 인하한 사유와 그 시점의 예산·예측. 저장한 값을 그대로 보여 준다
export default function PolicyBasisDetail({ history }: { history: PolicyHistory }) {
  const b = history.basis;
  return (
    <div className="space-y-3 rounded-lg bg-slate-50 px-3 py-3">
      <p className="whitespace-pre-line break-words text-sm"><b>사유</b> {history.reason ?? "-"}</p>
      <p className="note">
        변경자 {history.changedBy == null ? "-" : `운영자 ${history.changedBy}번`}
        {b && ` · 직전 요율 ${pctText(b.previousRate)}`}
        {b && b.year != null && ` · ${b.month}월 예산 기준, 예측 모델 ${b.modelVersion}`}
      </p>
      {b && b.year != null ? (
        <BasisGrid basis={b} />
      ) : (
        <p className="note">저장한 달의 월 예산이 없어 예산과 예측은 남지 않았어요.</p>
      )}
    </div>
  );
}
