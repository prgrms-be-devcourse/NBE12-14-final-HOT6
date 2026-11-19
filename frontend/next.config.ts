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
