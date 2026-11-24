// 서버의 정산 날짜 기준(한국 시간)과 같은 달력으로 날짜를 만든다. 화면이 열린 기기의 시간대가 달라도 한국 날짜를 쓴다.
const kstParts = (d: Date) => {
  const p = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(d);
  const get = (t: string) => Number(p.find((x) => x.type === t)?.value);
  return { y: get("year"), m: get("month"), d: get("day") };
};

const pad = (n: number) => String(n).padStart(2, "0");

export const ymd = (d: Date) => {
  const { y, m, d: day } = kstParts(d);
  return `${y}-${pad(m)}-${pad(day)}`;
};

export const addDays = (d: Date, days: number) => new Date(d.getTime() + days * 86_400_000);

// n 개월 전 달의 "yyyy-MM" (0 이면 이번 달)
export const monthsAgo = (d: Date, n: number) => {
  const { y, m } = kstParts(d);
  const idx = y * 12 + (m - 1) - n;
  return `${Math.floor(idx / 12)}-${pad((idx % 12) + 1)}`;
};
