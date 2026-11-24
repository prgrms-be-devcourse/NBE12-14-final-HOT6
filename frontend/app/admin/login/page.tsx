import LoginForm from "@/components/LoginForm";
import HomeLink from "@/components/HomeLink";
import Logo from "@/components/Logo";

export default function AdminLoginPage() {
  return (
    <div className="web-zoom web-screen relative flex items-center justify-center px-5">
      <HomeLink />
      <div className="card w-full max-w-sm !p-8">
        <Logo className="text-2xl" />
        <p className="mt-1 text-sm text-zinc-500">운영팀 콘솔</p>
        <div className="mt-6"><LoginForm audience="admin" /></div>
      </div>
    </div>
  );
}
