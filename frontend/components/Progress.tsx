export default function Progress({ value, max }: { value: number; max: number }) {
  const pct = Math.min(100, Math.round((value / max) * 100));
  return (
    <div
      className="h-2.5 overflow-hidden rounded-full bg-zinc-200"
      role="progressbar"
      aria-valuenow={value}
      aria-valuemin={0}
      aria-valuemax={max}
    >
      <div className="h-full bg-emerald-600" style={{ width: `${pct}%` }} />
    </div>
  );
}
