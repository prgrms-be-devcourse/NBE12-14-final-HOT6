import type { NextConfig } from "next";

// 브라우저는 같은 오리진(/api/...)으로 호출하고, Next 가 백엔드로 넘긴다 (CORS 불필요)
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${BACKEND_URL}/api/:path*` },
      // API 테스트 화면이 OpenAPI 문서를 읽어 엔드포인트 목록을 자동 구성한다
      { source: "/v3/api-docs", destination: `${BACKEND_URL}/v3/api-docs` },
    ];
  },
  cacheComponents: true,
  partialPrefetching: true,
  // 개발 중 모든 페이지를 자동으로 즉시 이동(instant navigation) 검증하지 않고, 페이지가 직접 `instant` 를 내보낸 곳만 검증한다.
  // 로그인 확인(AuthGuard) 뒤에 브라우저에서 데이터를 불러오는 화면이 대부분이라, 서버가 미리 그릴 내용이 없어 매번 경고가 난다.
  experimental: {
    instantInsights: { validationLevel: "manual-warning" },
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
