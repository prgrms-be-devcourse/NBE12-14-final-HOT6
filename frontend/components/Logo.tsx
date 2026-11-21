import Link from "next/link";

export default function Logo({ className = "", inverse = false }: { className?: string; inverse?: boolean }) {
  return (
    <Link href="/" className={`inline-flex items-center gap-2 font-extrabold tracking-tight ${className}`} aria-label="DevPay 홈">
      <span className={`flex h-7 w-7 items-center justify-center rounded-md text-sm font-extrabold ${inverse ? "bg-white text-brand-600" : "bg-brand-600 text-white"}`}>D</span>
      DevPay
    </Link>
  );
}
