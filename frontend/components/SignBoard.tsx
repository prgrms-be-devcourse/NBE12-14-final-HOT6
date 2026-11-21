import { ICONS } from "@/components/icons";

export type SignRow = { label: string; value: string; tone?: "pending" };

type Props = { rows: SignRow[]; label: string };

export default function SignBoard({ rows, label }: Props) {
  return (
    <section className="sign" aria-label={label}>
      {rows.map((r) => (
        <div key={r.label} className={`sign-row ${r.tone === "pending" ? "sign-row-wait" : ""}`}>
          <span className="flex items-center gap-2">
            {r.tone === "pending" ? ICONS.trendUp : ICONS.arrowRight}
            {r.label}
          </span>
          <b className="text-[16px] font-bold tabular-nums">{r.value}</b>
        </div>
      ))}
    </section>
  );
}
