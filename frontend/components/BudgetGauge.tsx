import { won } from "@/lib/format";

type Props = {
  total: number; // 이월을 반영한 예산 총액
  paid: number;
  reserved: number;
  remaining: number;
  contingency?: number; // 있으면 대비금 위치에 선을 긋는다
  dark?: boolean; // 어두운 바탕 위에 그릴 때
};

// 예산 게이지: 왼쪽부터 집행, 확보, 남음. 칸마다 비율과 금액을 아래에 적는다
export default function BudgetGauge({ total, paid, reserved, remaining, contingency, dark = false }: Props) {
  const share = (v: number) => (total > 0 ? Math.min(100, Math.max(0, (v / total) * 100)) : 0);
  const parts = [
    { label: "집행", value: paid, color: dark ? "bg-white" : "bg-blue-600" },
    { label: "확보", value: reserved, color: "bg-amber-500" },
    { label: "남음", value: remaining, color: dark ? "bg-white/25 ring-1 ring-white/70" : "bg-emerald-100 ring-1 ring-emerald-600" },
  ];
  const paidWidth = share(paid);

  return (
    <div className="w-full">
      <div
        className={`relative flex h-3 w-full overflow-hidden rounded-full ${dark ? "bg-white/25" : "bg-emerald-100"}`}
        role="img"
        aria-label={parts.map((p) => `${p.label} ${Math.round(share(p.value))}%`).join(", ")}
      >
        <span className={`h-full ${parts[0].color}`} style={{ width: `${paidWidth}%` }} />
        <span className="h-full bg-amber-500" style={{ width: `${Math.min(100 - paidWidth, share(reserved))}%` }} />
        {contingency != null && <span className={`absolute inset-y-0 w-0.5 ${dark ? "bg-white" : "bg-slate-500"}`} style={{ left: `${100 - share(contingency)}%` }} />}
      </div>
      {/* 칸 너비에 맞춰 놓되, 좁은 칸의 숫자도 잘리지 않게 한다 */}
      <dl className="mt-1.5 flex flex-wrap gap-x-3 gap-y-1">
        {parts.map((p, i) => (
          <div key={p.label} className={`min-w-max ${i === parts.length - 1 ? "text-right" : "text-left"}`} style={{ flex: `${share(p.value)} 0 0%` }}>
            <dt className={`flex items-center gap-1 text-[12px] ${i === parts.length - 1 ? "justify-end" : ""} ${dark ? "text-white/80" : "text-slate-600"}`}>
              <i className={`inline-block h-2 w-2 rounded-full ${p.color}`} />
              {p.label} {Math.round(share(p.value))}%
            </dt>
            <dd className="text-[13px] font-bold tabular-nums">{won(p.value)}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
