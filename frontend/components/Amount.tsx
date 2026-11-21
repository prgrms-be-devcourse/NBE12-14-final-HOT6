import { signed, won } from "@/lib/format";

type Kind = "in" | "out" | "cashback" | "wait" | "plain";

const COLOR: Record<Kind, string> = {
  in: "amt-in",
  out: "amt-out",
  cashback: "amt-cb",
  wait: "amt-wait",
  plain: "",
};

type Props = { value: number; kind?: Kind; size?: "sm" | "md" | "lg" | "xl"; sign?: boolean; className?: string };

const SIZE = { sm: "text-sm font-medium", md: "text-base font-bold", lg: "text-[26px] font-bold", xl: "text-[40px] font-bold leading-tight" };

export default function Amount({ value, kind = "plain", size = "md", sign = false, className = "" }: Props) {
  return <span className={`tabular-nums tracking-tight ${SIZE[size]} ${COLOR[kind]} ${className}`}>{sign ? signed(value) : won(value)}</span>;
}
