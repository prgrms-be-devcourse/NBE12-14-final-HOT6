import Link from "next/link";

export default function Logo({ className = "" }: { className?: string }) {
  return (
    <Link href="/" className={`inline-flex items-center gap-2 font-bold tracking-tight ${className}`} aria-label="DevPay 홈">
      <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-blue-600 text-sm text-white">D</span>
      DevPay
    </Link>
  );
}
