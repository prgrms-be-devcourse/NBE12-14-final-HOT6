import { signed, won } from "@/lib/format";

type Kind = "in" | "out" | "cashback" | "cashbackDeep" | "wait" | "plain";

const COLOR: Record<Kind, string> = {
  in: "amt-in",
  out: "amt-out",
  cashback: "amt-cb",
  cashbackDeep: "text-emerald-800", // 홈 카드의 캐시백 잔액(짙은 초록)
  wait: "amt-wait",
  plain: "",
};

type Props = { value: number; kind?: Kind; size?: "sm" | "md" | "lg" | "xl"; sign?: boolean; className?: string };

const SIZE = { sm: "text-sm font-medium", md: "text-base font-bold", lg: "text-[26px] font-bold", xl: "text-[40px] font-bold leading-tight" };

export default function Amount({ value, kind = "plain", size = "md", sign = false, className = "" }: Props) {
  return <span className={`tabular-nums tracking-tight ${SIZE[size]} ${COLOR[kind]} ${className}`}>{sign ? signed(value) : won(value)}</span>;
}
