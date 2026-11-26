import type { ReactNode } from "react";

const svg = (children: ReactNode, size = 24) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
    {children}
  </svg>
);

// 앱 전체에서 쓰는 선 아이콘 (currentColor)
export const ICONS = {
  home: svg(<><path d="M3 11.5 12 4l9 7.5" /><path d="M5.5 10v9.5h13V10" /><path d="M10 19.5v-5h4v5" /></>),
  qr: svg(<><rect x="3.5" y="3.5" width="6" height="6" rx="1" /><rect x="14.5" y="3.5" width="6" height="6" rx="1" /><rect x="3.5" y="14.5" width="6" height="6" rx="1" /><path d="M14.5 14.5h2.5v2.5h-2.5zM17 20.5h3.5M20.5 17v3.5" /></>),
  store: svg(<><path d="M4 9.5 5.5 4h13L20 9.5" /><path d="M4 9.5c0 1.4 1.1 2.5 2.5 2.5S9 10.9 9 9.5c0 1.4 1.1 2.5 2.5 2.5h1c1.4 0 2.5-1.1 2.5-2.5 0 1.4 1.1 2.5 2.5 2.5S20 10.9 20 9.5" /><path d="M5.5 12v8h13v-8" /><path d="M10 20v-4.5h4V20" /></>),
  gift: svg(<><rect x="3.5" y="8.5" width="17" height="4" rx="1" /><path d="M5 12.5V20h14v-7.5" /><path d="M12 8.5V20" /><path d="M12 8.5C10.5 8.5 8 8 8 6s2.5-2.2 4 2.5c1.5-4.7 4-4.5 4-2.5s-2.5 2.5-4 2.5Z" /></>),
  user: svg(<><circle cx="12" cy="8.5" r="3.7" /><path d="M4.5 20c.6-3.6 3.5-5.5 7.5-5.5s6.9 1.9 7.5 5.5" /></>),
  receipt: svg(<><path d="M6 3.5h12V21l-3-2-3 2-3-2-3 2z" /><path d="M9 8h6M9 12h6" /></>),
  logout: svg(<><path d="M10 4.5H5.5v15H10" /><path d="M14 8.5 18 12l-4 3.5M18 12H9.5" /></>),
  chart: svg(<><path d="M4 20V4" /><path d="M4 20h16" /><path d="M8 16v-5M12 16V8M16 16v-3" /></>, 20),
  sliders: svg(<><path d="M4 7h10M18 7h2M4 17h2M10 17h10" /><circle cx="16" cy="7" r="2" /><circle cx="8" cy="17" r="2" /></>, 20),
  check: svg(<path d="m5 12.5 4.5 4.5L19 7.5" />, 28),
  alert: svg(<><path d="M12 7v6" /><circle cx="12" cy="17" r="0.6" /></>, 28),
  clock: svg(<><circle cx="12" cy="12" r="8" /><path d="M12 8v4l2.5 2" /></>, 28),
  back: svg(<path d="m15 5-7 7 7 7" />, 22),
  arrowIn: svg(<path d="M12 5v14M6 13l6 6 6-6" />, 18),
  arrowOut: svg(<path d="M12 19V5M6 11l6-6 6 6" />, 18),
  arrowRight: svg(<path d="M5 12h14M13 6l6 6-6 6" />, 16),
  chevronDown: svg(<path d="m6 9.5 6 6 6-6" />, 18),
  trendUp: svg(<path d="M4 17 10 11l4 4 6-7M15 8h5v5" />, 16),
  star: svg(<path d="m12 4 2.4 5 5.4.7-4 3.7 1 5.4-4.8-2.7-4.8 2.7 1-5.4-4-3.7 5.4-.7Z" />, 18),
  payout: svg(<><rect x="3" y="6" width="18" height="12" rx="2" /><circle cx="12" cy="12" r="2.5" /><path d="M6.5 9.5v5M17.5 9.5v5" /></>, 20),
  scale: svg(<><path d="M12 4v16M7 20h10" /><path d="M5 8h14" /><path d="M5 8l-2.5 6a3 3 0 0 0 5 0L5 8ZM19 8l-2.5 6a3 3 0 0 0 5 0L19 8Z" /></>, 20),
} as const;
