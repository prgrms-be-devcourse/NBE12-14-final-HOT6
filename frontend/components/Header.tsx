import Link from "next/link";
import { ICONS } from "@/components/icons";

type Props = { title: string; back?: string; right?: React.ReactNode };

export default function Header({ title, back, right }: Props) {
  return (
    <header className="appbar">
      {back && (
        <Link href={back} aria-label="뒤로" className="-ml-1 flex h-9 w-9 items-center justify-center rounded-full hover:bg-white/10">
          {ICONS.back}
        </Link>
      )}
      <h1 className={`flex-1 text-[17px] font-bold ${back ? "" : "pl-1"}`}>{title}</h1>
      {right && <span className="text-[13px] font-medium text-white/85">{right}</span>}
      <Link href="/" aria-label="홈으로" className="-mr-1 flex h-9 w-9 items-center justify-center rounded-full hover:bg-white/10">
        {ICONS.home}
      </Link>
    </header>
  );
}
