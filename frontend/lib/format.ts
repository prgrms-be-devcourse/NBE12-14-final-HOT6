export const won = (n: number) => `${n.toLocaleString("ko-KR")}원`;
export const signed = (n: number) =>
  `${n > 0 ? "+" : n < 0 ? "−" : ""}${Math.abs(n).toLocaleString("ko-KR")}`;
export const mmss = (sec: number) =>
  `${String(Math.floor(sec / 60)).padStart(2, "0")}:${String(sec % 60).padStart(2, "0")}`;
export const safeNext = (next: string | undefined, fallback: string) =>
  next && next.startsWith("/") && !next.startsWith("//") ? next : fallback;
