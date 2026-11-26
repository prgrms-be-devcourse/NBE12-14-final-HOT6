import Link from "next/link";

type Item = { href: string; label: string; star?: boolean };
const LANES: { title: string; note?: string; items: Item[] }[] = [
  { title: "회원 앱 · 가입 / 로그인", items: [
    { href: "/signup", label: "① 회원가입", star: true }, { href: "/pin-setup", label: "② 간편비밀번호 설정" },
    { href: "/login", label: "③ 로그인", star: true }, { href: "/pin", label: "④ 간편비밀번호 입력" }, { href: "/locked", label: "⑤ 잠금 안내" } ] },
  { title: "회원 앱 · 지갑 / 충전 / 환불", items: [
    { href: "/wallet", label: "⑥ 지갑 홈", star: true }, { href: "/wallet/charge", label: "⑦ 충전 (바로 충전)", star: true },
    { href: "/wallet?charged=50000", label: "⑨ 충전 후 지갑 홈", star: true }, { href: "/wallet/refund", label: "⑩ 지갑 환불" } ] },
  { title: "회원 앱 · 결제 / 캐시백", items: [
    { href: "/pay/scan", label: "⑪ QR 스캔 · 미리보기", star: true }, { href: "/stores", label: "가맹점 찾기 (추가)" }, { href: "/pay/receipt", label: "⑫ 결제 완료 · 영수증", star: true },
    { href: "/pay/history", label: "⑬ 결제 내역 · 취소" }, { href: "/cashback", label: "⑭⑮ 캐시백 내역" } ] },
  { title: "회원 앱 · 마이페이지", items: [
    { href: "/my", label: "⑯ 마이페이지" }, { href: "/my/profile", label: "⑰ 내정보 수정" }, { href: "/my/withdraw", label: "⑱ 회원 탈퇴 · 정산" } ] },
  { title: "사장님 앱", items: [
    { href: "/merchant/register", label: "① 가맹점 등록", star: true }, { href: "/merchant/edit", label: "② 가맹점 정보" },
    { href: "/merchant/qr", label: "③ QR 생성", star: true }, { href: "/merchant/paid", label: "④ QR 결제 완료 확인", star: true },
    { href: "/merchant/payments", label: "⑤ 결제 내역" }, { href: "/merchant/daily", label: "⑥ 일 정산" }, { href: "/merchant/monthly", label: "⑦ 월 정산" } ] },
  { title: "운영팀", items: [
    { href: "/admin", label: "① 대시보드" }, { href: "/admin/policy", label: "② 정책 · 예산 관리", star: true }, { href: "/admin/settle", label: "③ 정산 · 원장 대사" } ] },
];

export default function DevMenuPage() {
  return (
    <main className="mx-auto max-w-4xl px-5 py-10">
      <h1 className="text-2xl font-semibold">화면 목록 <span className="text-base font-normal text-zinc-500">(개발용)</span></h1>
      <p className="mt-1 text-sm text-zinc-500">전체 화면 목록 · <span className="text-amber-600">★</span> = 11/24 중간 제출 필수 화면</p>
      <Link href="/dev/api" className="btn btn-primary mt-4 !w-auto">API 테스트 열기 →</Link>
      <div className="mt-6 space-y-4">
        {LANES.map((l) => (
          <section key={l.title} className="card">
            <h2 className="mb-2 font-semibold">{l.title}</h2>
            <div className="flex flex-wrap gap-2">
              {l.items.map((i) => (
                <Link key={i.href} href={i.href} className="chip hover:bg-amber-50">{i.star && <span className="mr-1 text-amber-600">★</span>}{i.label}</Link>
              ))}
            </div>
          </section>
        ))}
      </div>
    </main>
  );
}
