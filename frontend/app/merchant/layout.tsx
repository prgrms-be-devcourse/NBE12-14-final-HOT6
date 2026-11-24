import AuthGuard from "@/components/AuthGuard";
import PhoneFrame from "@/components/PhoneFrame";

// 로그인·가맹점 등록 화면은 로그인 없이 연다
const PUBLIC_PATHS = ["/merchant/login", "/merchant/register"];

export default function MerchantLayout({ children }: { children: React.ReactNode }) {
  return (
    <PhoneFrame>
      <AuthGuard audience="merchant" publicPaths={PUBLIC_PATHS}>{children}</AuthGuard>
    </PhoneFrame>
  );
}
