"use client";

import { useState } from "react";

type PostcodeData = { roadAddress: string; address: string };
type PostcodeOptions = { oncomplete: (data: PostcodeData) => void };
declare global {
  interface Window {
    daum?: { Postcode: new (options: PostcodeOptions) => { open: () => void } };
  }
}

const SCRIPT = "https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";
let loading: Promise<void> | null = null;

function loadPostcode(): Promise<void> {
  if (window.daum?.Postcode) return Promise.resolve();
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("주소 검색을 불러오지 못했어요"));
    document.head.appendChild(script);
  }).catch((e) => {
    loading = null;
    throw e;
  });
  return loading;
}

// 다음(카카오) 우편번호 서비스. 키가 필요 없고, 고른 주소 문자열만 돌려준다. 좌표 변환은 서버가 한다.
export default function AddressSearch({ onSelect, disabled }: { onSelect: (address: string) => void; disabled?: boolean }) {
  const [error, setError] = useState("");
  const open = async () => {
    setError("");
    try {
      await loadPostcode();
      new window.daum!.Postcode({ oncomplete: (d) => onSelect(d.roadAddress || d.address) }).open();
    } catch (e) {
      setError(e instanceof Error ? e.message : "주소 검색을 열지 못했어요");
    }
  };
  return (
    <>
      <button type="button" onClick={open} disabled={disabled} className="btn btn-outline btn-sm !w-auto shrink-0 disabled:opacity-40">주소 찾기</button>
      {error && <p role="alert" className="basis-full text-[13px] font-medium text-red-700">{error}</p>}
    </>
  );
}
