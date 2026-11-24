"use client";

import { useEffect, useState } from "react";
import { mmss } from "@/lib/format";

// warnBelow 초 이하로 남으면 warnClassName 을 더해 눈에 띄게 한다(예: 빨간색)
type Props = { seconds: number; className?: string; onExpire?: () => void; warnBelow?: number; warnClassName?: string };

export default function Countdown({ seconds, className, onExpire, warnBelow, warnClassName }: Props) {
  const [left, setLeft] = useState(seconds);
  const [base, setBase] = useState(seconds);

  if (base !== seconds) {
    setBase(seconds);
    setLeft(seconds);
  }

  useEffect(() => {
    const id = setInterval(() => setLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(id);
  }, [seconds]);

  useEffect(() => {
    if (left === 0) onExpire?.();
  }, [left, onExpire]);

  const warn = warnBelow !== undefined && left <= warnBelow;
  return <span className={warn && warnClassName ? `${className ?? ""} ${warnClassName}` : className}>{mmss(left)}</span>;
}
