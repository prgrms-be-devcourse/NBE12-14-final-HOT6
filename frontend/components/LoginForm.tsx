"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { type Audience, canEnter, clearSession, HOME_PATH, saveLogin } from "@/lib/auth";

// 이메일·비밀번호로 로그인한다. 이 화면(audience)에 들어올 수 없는 역할의 계정이면 안내한다.
// - next: 이용자 로그인 뒤 (간편비밀번호 확인을 거쳐) 돌아갈 화면. 예: 가맹점 등록
// - 점주 로그인에 이용자(USER) 계정으로 들어오면 로그인은 유지하고 가맹점 등록을 안내한다(가맹점을 등록하면 점주가 된다)
export default function LoginForm({ audience, next }: { audience: Audience; next?: string }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [userOnly, setUserOnly] = useState(false); // 점주 로그인에 이용자 계정으로 들어온 경우

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setUserOnly(false);
    setBusy(true);
    try {
      const res = await api<{ accessToken: string; tokenType: string; expiresIn: number }>("/auth/login", {
        method: "POST",
        body: { email, password },
      });
      const session = saveLogin(res);
      if (!session) throw new ApiError("로그인 정보를 해석하지 못했어요. 다시 시도해 주세요.", 0);
      if (!canEnter(session.role, audience)) {
        // 이용자 계정이 점주 로그인에 들어온 경우: 토큰을 지우지 않는다. 가맹점을 등록하면 점주가 되므로 등록으로 안내한다
        if (audience === "merchant" && session.role === "USER") {
          setUserOnly(true);
          return;
        }
        clearSession();
        throw new ApiError("이 화면에서는 로그인할 수 없는 계정이에요. 올바른 로그인 화면을 이용해 주세요.", 403);
      }
      router.push(audience === "user" && next ? `/pin?next=${encodeURIComponent(next)}` : HOME_PATH[audience]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "로그인에 실패했어요. 잠시 뒤 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="space-y-3" onSubmit={submit}>
      <label className="block">
        <span className="label">이메일</span>
        <input className="field" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label className="block">
        <span className="label">비밀번호</span>
        <input className="field" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{error}</p>}
      {userOnly && (
        <div role="status" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-[13px] text-amber-900">
          <b>이용자 계정이에요.</b> 점주 기능은 가맹점을 등록한 계정만 쓸 수 있어요. 가맹점을 등록하면 이 계정이 점주가 돼요.
          <Link href="/merchant/register" className="mt-2 block font-bold text-brand-700 underline underline-offset-2">가맹점 등록하기 →</Link>
        </div>
      )}
      <button type="submit" className="btn btn-primary" disabled={busy || !email || !password}>{busy ? "로그인 중..." : "로그인"}</button>
    </form>
  );
}
