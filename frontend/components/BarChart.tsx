type Series = { name: string; color: string; values: number[] };
type Props = { labels: string[]; series: Series[]; label: string; reserveLine?: number };

export default function BarChart({ labels, series, label, reserveLine }: Props) {
  const max = Math.max(...series.flatMap((s) => s.values), reserveLine ?? 0) || 1;
  return (
    <div>
      <div className="relative flex h-40 items-end gap-3 border-b border-zinc-300" role="img" aria-label={label}>
        {reserveLine !== undefined && (
          <div className="absolute inset-x-0 border-t border-dashed border-red-600" style={{ bottom: `${(reserveLine / max) * 100}%` }}>
            <span className="absolute -top-4 right-0 text-[11px] text-red-700">대비금 기준</span>
          </div>
        )}
        {labels.map((l, i) => (
          <div key={l} className="flex h-full flex-1 items-end justify-center gap-0.5">
            {series.map((s) => (
              <div key={s.name} className="w-full max-w-5 rounded-t" style={{ height: `${(s.values[i] / max) * 100}%`, background: s.color }} />
            ))}
          </div>
        ))}
      </div>
      <div className="mt-1 flex gap-3">{labels.map((l) => <span key={l} className="flex-1 text-center text-[11px] text-zinc-500">{l}</span>)}</div>
      <div className="mt-2 flex flex-wrap gap-3 text-xs text-zinc-600">
        {series.map((s) => <span key={s.name} className="flex items-center gap-1"><i className="inline-block h-2.5 w-2.5 rounded-sm" style={{ background: s.color }} />{s.name}</span>)}
      </div>
    </div>
  );
}
