import Image from "next/image";

// 홈 맨 위의 안내 배너: 왼쪽에 문구, 오른쪽에 마스코트. 눌러서 이동하지 않는다
export default function PromoBanner() {
  return (
    <section aria-label="데브페이 안내" className="mb-2.5 flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2.5">
      <p className="min-w-0 flex-1 break-keep text-center text-[15px] font-extrabold leading-snug tracking-tight text-brand-700">
        자라나는 동네 상권
        <br />
        멈추지 않는 혜택의 지속성
        <br />
        데브페이와 함께!
      </p>
      <Image src="/banner-mascot.webp" alt="카드를 들고 윙크하는 데브페이 새싹 마스코트" width={381} height={360} loading="eager" className="h-[86px] w-auto shrink-0" />
    </section>
  );
}
