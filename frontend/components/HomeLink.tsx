import Link from "next/link";
import { ICONS } from "@/components/icons";

export default function HomeLink({ className = "" }: { className?: string }) {
  return (
    <Link
      href="/"
      aria-label="홈으로"
      className={`absolute right-3 top-3 z-10 flex h-10 w-10 items-center justify-center rounded-full text-slate-700 hover:bg-slate-100 ${className}`}
    >
      {ICONS.home}
    </Link>
  );
}
