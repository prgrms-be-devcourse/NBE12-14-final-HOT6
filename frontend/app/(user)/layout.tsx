import AuthGuard from "@/components/AuthGuard";
import PhoneFrame from "@/components/PhoneFrame";

// 로그인·가입·간편비밀번호 화면은 로그인 없이 연다
const PUBLIC_PATHS = ["/login", "/signup", "/pin", "/pin-setup", "/locked"];

export default function UserLayout({ children }: { children: React.ReactNode }) {
  return (
    <PhoneFrame>
      <AuthGuard audience="user" publicPaths={PUBLIC_PATHS}>{children}</AuthGuard>
    </PhoneFrame>
  );
}
