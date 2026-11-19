import Link from "next/link";

type Props = { title: string; back?: string; right?: React.ReactNode };

export default function Header({ title, back, right }: Props) {
  return (
    <header className="flex items-center gap-2 px-5 pb-3 pt-6">
      {back && (
        <Link href={back} aria-label="뒤로" className="-ml-1 px-1 text-2xl leading-none">
          ‹
        </Link>
      )}
      <h1 className="flex-1 text-xl font-medium">{title}</h1>
      {right && <span className="text-[13px] text-zinc-500">{right}</span>}
    </header>
  );
}
