"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { type Audience, canEnter, clearSession, HOME_PATH, saveLogin } from "@/lib/auth";

// 이메일·비밀번호로 로그인한다. 이 화면(audience)에 들어올 수 없는 역할의 계정이면 토큰을 저장하지 않고 안내한다.
export default function LoginForm({ audience }: { audience: Audience }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await api<{ accessToken: string; tokenType: string; expiresIn: number }>("/auth/login", {
        method: "POST",
        body: { email, password },
      });
      const session = saveLogin(res);
      if (!session) throw new ApiError("로그인 정보를 해석하지 못했어요. 다시 시도해 주세요.", 0);
      if (!canEnter(session.role, audience)) {
        clearSession();
        throw new ApiError("이 화면에서는 로그인할 수 없는 계정이에요. 올바른 로그인 화면을 이용해 주세요.", 403);
      }
      router.push(HOME_PATH[audience]);
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
      <button type="submit" className="btn btn-primary" disabled={busy || !email || !password}>{busy ? "로그인 중..." : "로그인"}</button>
    </form>
  );
}
