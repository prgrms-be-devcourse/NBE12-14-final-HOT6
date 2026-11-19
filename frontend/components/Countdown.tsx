"use client";

import { useEffect, useState } from "react";
import { mmss } from "@/lib/format";

type Props = { seconds: number; className?: string; onExpire?: () => void };

export default function Countdown({ seconds, className, onExpire }: Props) {
  const [left, setLeft] = useState(seconds);

  useEffect(() => {
    setLeft(seconds);
    const id = setInterval(() => setLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(id);
  }, [seconds]);

  useEffect(() => {
    if (left === 0) onExpire?.();
  }, [left, onExpire]);

  return <span className={className}>{mmss(left)}</span>;
}
