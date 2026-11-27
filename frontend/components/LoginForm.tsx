"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { type Audience, canEnter, clearSession, HOME_PATH, LOGIN_API, saveLogin } from "@/lib/auth";

// 이메일·비밀번호로 로그인한다. 이 화면(audience)에 들어올 수 없는 역할의 계정이면 안내한다.
// - next: 이용자 로그인 뒤 (간편비밀번호 확인을 거쳐) 돌아갈 화면
// - 점주 화면은 점주 로그인 API 를, 회원·운영자 화면은 기존 로그인 API 를 부른다. 같은 이메일의 계정은 이것으로 구분된다
export default function LoginForm({ audience, next }: { audience: Audience; next?: string }) {
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
      const res = await api<{ accessToken: string; tokenType: string; expiresIn: number }>(LOGIN_API[audience], {
        method: "POST",
        body: { email, password },
      });
      const session = saveLogin(res);
      if (!session) throw new ApiError("로그인 정보를 해석하지 못했어요. 다시 시도해 주세요.", 0);
      if (!canEnter(session.role, audience)) {
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
        {/* 로그인은 비밀번호 관리 도구를 사용하는 사용자의 편의성을 위해 붙여넣기를 막지 않는다 (가입 화면만 막는다) */}
        <input className="field" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      {error && <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-[13px] font-medium text-red-600">{error}</p>}
      <button type="submit" className="btn btn-primary" disabled={busy || !email || !password}>{busy ? "로그인 중..." : "로그인"}</button>
    </form>
  );
}
